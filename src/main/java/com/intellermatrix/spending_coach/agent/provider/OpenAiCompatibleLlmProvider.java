package com.intellermatrix.spending_coach.agent.provider;

import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderRequest;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderResponse;
import com.intellermatrix.spending_coach.agent.dto.provider.ToolCall;
import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.provider.config.ProviderConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Wire-format request/response shapes below are this class's own serialization
// detail (plain Map/JsonNode handling, no shared agent/dto types) - Gemini and Groq
// both speak the identical OpenAI chat-completions format, confirmed from the
// Milestone 1 spike's own request/response handling.
@RequiredArgsConstructor
public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String MALFORMED_ARGUMENTS_ERROR = "malformed tool-call arguments JSON";
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

        String responseBody = restClient.post()
            .uri(CHAT_COMPLETIONS_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + config.apiKey())
            .body(writeJson(requestBody))
            .retrieve()
            .body(String.class);

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
        JsonNode message = root.get("choices").get(0).get("message");
        String content = message.hasNonNull("content") ? message.get("content").asString() : null;
        List<ToolCall> toolCalls = toToolCalls(message.get("tool_calls"));
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
            Map<String, Object> arguments = parseArguments(function.get("arguments").asString());
            toolCalls.add(new ToolCall(id, toolName, arguments));
        }
        return toolCalls;
    }

    private Map<String, Object> parseArguments(String argumentsJson) {
        try {
            return MAPPER.readValue(argumentsJson, new TypeReference<Map<String, Object>>() { });
        } catch (JacksonException e) {
            // The model emitted unparseable/truncated JSON for a tool call's arguments -
            // this becomes a normal failed-schema-validation or unknown-shape result once
            // dispatched, not an unhandled parse exception crashing the run.
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("parseError", MALFORMED_ARGUMENTS_ERROR);
            error.put("rawArguments", argumentsJson);
            return error;
        }
    }

    private String writeJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    private JsonNode readJson(String json) {
        return MAPPER.readTree(json);
    }
}
