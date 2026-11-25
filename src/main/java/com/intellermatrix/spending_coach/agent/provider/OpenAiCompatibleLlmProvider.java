package com.intellermatrix.spending_coach.agent.provider;

import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderRequest;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderResponse;
import com.intellermatrix.spending_coach.agent.dto.provider.ToolCall;
import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.provider.config.ProviderConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Wire-format request/response shapes below are this class's own serialization
// detail (plain Map/JsonNode handling, no shared agent/dto types) - Gemini and Groq
// both speak the identical OpenAI chat-completions format, confirmed from the
// Milestone 1 spike's own request/response handling.
@RequiredArgsConstructor
public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String MALFORMED_ARGUMENTS_ERROR = "malformed tool-call arguments JSON";
    private static final String MALFORMED_ARGUMENTS_TOOL_SUFFIX = ":malformed-arguments";
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final ProviderConfig config;
    private final RestClient.Builder builder;

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public ProviderResponse chat(ProviderRequest request) {
        RestClient restClient = builder.baseUrl(config.baseUrl()).build();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", config.model());
        requestBody.put("messages", request.messages().stream().map(this::toWireMessage).toList());
        if (!request.tools().isEmpty()) {
            requestBody.put("tools", request.tools().stream().map(this::toWireTool).toList());
        }

        String responseBody;
        try {
            responseBody = restClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + config.apiKey())
                .body(writeJson(requestBody))
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            if (e.getStatusCode().equals(HttpStatus.TOO_MANY_REQUESTS) || e.getStatusCode().is5xxServerError()) {
                throw new ProviderUnavailableException(
                    String.format(Locale.ROOT, "%s returned HTTP %d", config.name(), statusCode), statusCode, e);
            }
            throw e;
        } catch (RestClientException e) {
            // Covers connection failures and a read-timeout - whether it surfaces as
            // ResourceAccessException (request execution) or a plain RestClientException
            // wrapping an IOException (response body extraction) - per this task's own
            // Step 3: catch RestClientResponseException/RestClientException broadly here.
            throw new ProviderUnavailableException(
                String.format(Locale.ROOT, "%s request failed: %s", config.name(), e.getMessage()), null, e);
        }

        return toProviderResponse(readJson(responseBody));
    }

    private Map<String, Object> toWireMessage(ChatMessage message) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("role", message.role());
        wire.put("content", message.content());
        if (message.toolCalls() != null) {
            wire.put("tool_calls", message.toolCalls().stream().map(this::toWireToolCall).toList());
        }
        if (message.toolCallId() != null) {
            wire.put("tool_call_id", message.toolCallId());
        }
        return wire;
    }

    private Map<String, Object> toWireToolCall(ToolCall toolCall) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", toolCall.toolName());
        function.put("arguments", writeJson(toolCall.arguments()));

        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("id", toolCall.id());
        wire.put("type", "function");
        wire.put("function", function);
        return wire;
    }

    private Map<String, Object> toWireTool(ToolDefinition tool) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.put("parameters", readJson(tool.jsonSchema()));

        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("type", "function");
        wire.put("function", function);
        return wire;
    }

    private ProviderResponse toProviderResponse(JsonNode root) {
        JsonNode choices = root.get("choices");
        JsonNode message = choices != null && !choices.isEmpty() ? choices.get(0).get("message") : null;
        String content = message != null && message.hasNonNull("content") ? message.get("content").asString() : null;
        List<ToolCall> toolCalls = message != null ? toToolCalls(message.get("tool_calls")) : null;
        ChatMessage chatMessage = new ChatMessage("assistant", content, toolCalls, null);

        JsonNode usage = root.get("usage");
        int promptTokens = usage != null && usage.hasNonNull("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
        int completionTokens = usage != null && usage.hasNonNull("completion_tokens")
            ? usage.get("completion_tokens").asInt() : 0;

        return new ProviderResponse(chatMessage, promptTokens, completionTokens);
    }

    private List<ToolCall> toToolCalls(JsonNode toolCallsNode) {
        if (toolCallsNode == null || toolCallsNode.isNull() || toolCallsNode.isEmpty()) {
            return null;
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode toolCallNode : toolCallsNode) {
            String id = toolCallNode.get("id").asString();
            JsonNode function = toolCallNode.get("function");
            String toolName = function.get("name").asString();
            String rawArguments = function.get("arguments").asString();
            toolCalls.add(toToolCall(id, toolName, rawArguments));
        }
        return toolCalls;
    }

    private ToolCall toToolCall(String id, String toolName, String rawArguments) {
        try {
            Map<String, Object> arguments = MAPPER.readValue(rawArguments, new TypeReference<Map<String, Object>>() { });
            return new ToolCall(id, toolName, arguments);
        } catch (JacksonException e) {
            // The model emitted unparseable/truncated JSON for a tool call's arguments.
            // The real tool name is deliberately NOT reused as-is: DefaultToolRegistry
            // looks tools up by an exact name() match, so mangling it here routes this
            // through that registry's existing "Unknown tool" error path uniformly for
            // every tool - including ask_user, which DefaultAgentLoopService special-cases
            // by an exact name match BEFORE ToolArgumentValidator ever runs, and would
            // otherwise silently pause the run with a null question instead of reporting
            // an error. Zero changes to DefaultAgentLoopService/DefaultToolRegistry/
            // ToolArgumentValidator - this plan's Global Constraints forbid touching the
            // first of those beyond Task 7's one later catch.
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("parseError", MALFORMED_ARGUMENTS_ERROR);
            arguments.put("rawArguments", rawArguments);
            return new ToolCall(id, toolName + MALFORMED_ARGUMENTS_TOOL_SUFFIX, arguments);
        }
    }

    private String writeJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    private JsonNode readJson(String json) {
        return MAPPER.readTree(json);
    }
}
