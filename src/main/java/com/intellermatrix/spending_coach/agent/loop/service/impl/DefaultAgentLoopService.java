package com.intellermatrix.spending_coach.agent.loop.service.impl;

import com.intellermatrix.spending_coach.agent.dto.loop.AgentRunOutcome;
import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderRequest;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderResponse;
import com.intellermatrix.spending_coach.agent.dto.provider.ToolCall;
import com.intellermatrix.spending_coach.agent.dto.tool.ToolExecutionResult;
import com.intellermatrix.spending_coach.agent.loop.SystemPromptBuilder;
import com.intellermatrix.spending_coach.agent.loop.config.AgentLoopProperties;
import com.intellermatrix.spending_coach.agent.loop.service.AgentLoopService;
import com.intellermatrix.spending_coach.agent.provider.LlmProvider;
import com.intellermatrix.spending_coach.agent.util.PendingAskUserCall;
import com.intellermatrix.spending_coach.agent.tool.ToolRegistry;
import com.intellermatrix.spending_coach.enums.AgentLoopMessage;
import com.intellermatrix.spending_coach.enums.RunStatus;
import com.intellermatrix.spending_coach.enums.ToolCatalog;
import com.intellermatrix.spending_coach.persistence.entity.AgentRun;
import com.intellermatrix.spending_coach.persistence.entity.AgentStep;
import com.intellermatrix.spending_coach.persistence.repository.AgentRunRepository;
import com.intellermatrix.spending_coach.persistence.repository.AgentStepRepository;
import com.intellermatrix.spending_coach.persistence.repository.BudgetProposalRepository;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class DefaultAgentLoopService implements AgentLoopService {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .build();

    private final LlmProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final BudgetProposalRepository budgetProposalRepository;
    private final Clock clock;
    private final AgentLoopProperties properties;

    @Override
    public AgentRunOutcome startRun(UUID userId, String question) {
        AgentRun run = new AgentRun(UUID.randomUUID(), userId, question, llmProvider.name(), RunStatus.RUNNING,
            null, null, clock.instant(), null);
        agentRunRepository.save(run);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", SystemPromptBuilder.build(LocalDate.now(clock)), null, null));
        messages.add(new ChatMessage("user", question, null, null));

        return runLoop(run, messages);
    }

    @Override
    public AgentRunOutcome resumeRun(UUID runId, String answer) {
        AgentRun run = agentRunRepository.findById(runId).orElseThrow();
        if (run.getConversationState() == null) {
            throw new IllegalStateException(
                String.format(Locale.ROOT, "run %s is not paused awaiting a user answer", runId));
        }

        List<ChatMessage> messages = new ArrayList<>(
            MAPPER.readValue(run.getConversationState(), new TypeReference<List<ChatMessage>>() { }));
        ToolCall askUserCall = PendingAskUserCall.findIn(messages).orElseThrow();
        messages.add(new ChatMessage("tool", answer, null, askUserCall.id()));

        run.setStatus(RunStatus.RUNNING);
        agentRunRepository.save(run);

        return runLoop(run, messages);
    }

    private AgentRunOutcome runLoop(AgentRun run, List<ChatMessage> messages) {
        LoopState state = new LoopState();
        while (true) {
            Optional<AgentRunOutcome> wallClockOutcome = checkWallClock(run, state);
            if (wallClockOutcome.isPresent()) {
                return wallClockOutcome.get();
            }

            ChatMessage reply = requestNextReply(messages, state);
            if (!hasToolCalls(reply)) {
                return complete(run, reply.content(), state);
            }

            messages.add(reply);
            Optional<AgentRunOutcome> stepOutcome = dispatchToolCalls(run, messages, reply.toolCalls(), state);
            if (stepOutcome.isPresent()) {
                return stepOutcome.get();
            }
        }
    }

    private Optional<AgentRunOutcome> checkWallClock(AgentRun run, LoopState state) {
        if (Duration.between(run.getCreatedAt(), clock.instant()).getSeconds() >= properties.maxWallClockSeconds()) {
            return Optional.of(complete(run, AgentLoopMessage.WALL_CLOCK_LIMIT_REACHED.text(), state));
        }
        return Optional.empty();
    }

    private ChatMessage requestNextReply(List<ChatMessage> messages, LoopState state) {
        ProviderResponse response = llmProvider.chat(new ProviderRequest(messages, toolRegistry.definitions()));
        state.tokensUsed += response.promptTokens() + response.completionTokens();
        return response.message();
    }

    private boolean hasToolCalls(ChatMessage reply) {
        return reply.toolCalls() != null && !reply.toolCalls().isEmpty();
    }

    private Optional<AgentRunOutcome> dispatchToolCalls(AgentRun run, List<ChatMessage> messages,
                                                          List<ToolCall> toolCalls, LoopState state) {
        for (ToolCall toolCall : toolCalls) {
            if (isAskUser(toolCall.toolName())) {
                return Optional.of(pauseForAskUser(run, messages, toolCall, state));
            }

            state.stepNumber++;
            messages.add(dispatch(run, state.stepNumber, toolCall));

            int repeatCount = state.repeatCounts.merge(canonicalKey(toolCall), 1, Integer::sum);
            if (repeatCount >= properties.loopAbortThreshold()) {
                return Optional.of(abortForRepeatedToolCall(run, state));
            }
            if (repeatCount == properties.loopNudgeThreshold()) {
                messages.add(new ChatMessage("tool", AgentLoopMessage.LOOP_NUDGE.text(), null, toolCall.id()));
            }

            if (state.stepNumber >= properties.maxSteps()) {
                return Optional.of(complete(run, AgentLoopMessage.STEP_LIMIT_REACHED.text(), state));
            }
        }
        return Optional.empty();
    }

    private String canonicalKey(ToolCall toolCall) {
        try {
            return toolCall.toolName() + CANONICAL_MAPPER.writeValueAsString(toolCall.arguments());
        } catch (JacksonException e) {
            return toolCall.toolName() + "#unserializable:" + System.identityHashCode(toolCall.arguments());
        }
    }

    private ChatMessage dispatch(AgentRun run, int stepNumber, ToolCall toolCall) {
        long startMillis = clock.millis();
        ToolExecutionResult result = toolRegistry.execute(run.getUserId(), toolCall.toolName(), toolCall.arguments());
        int latencyMs = (int) (clock.millis() - startMillis);

        String argumentsJson = serializeOrNull(toolCall.arguments());
        agentStepRepository.save(new AgentStep(run.getId(), stepNumber, toolCall.toolName(), argumentsJson,
            result.resultSummary(), latencyMs, null, result.error()));

        String content = serializeToolResult(result);
        return new ChatMessage("tool", content, null, toolCall.id());
    }

    private String serializeOrNull(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            return null;
        }
    }

    private String serializeToolResult(ToolExecutionResult result) {
        try {
            return MAPPER.writeValueAsString(result);
        } catch (JacksonException e) {
            return result.resultSummary() != null ? result.resultSummary() : "tool result could not be serialized";
        }
    }

    private AgentRunOutcome complete(AgentRun run, String answer, LoopState state) {
        finalizeTotals(run, state);
        run.setStatus(RunStatus.COMPLETED);
        agentRunRepository.save(run);
        return new AgentRunOutcome(run.getId(), RunStatus.COMPLETED, answer, null);
    }

    private AgentRunOutcome abortForRepeatedToolCall(AgentRun run, LoopState state) {
        finalizeTotals(run, state);
        run.setStatus(RunStatus.FAILED);
        agentRunRepository.save(run);
        return new AgentRunOutcome(run.getId(), RunStatus.FAILED, AgentLoopMessage.LOOP_ABORTED.text(), null);
    }

    private boolean isAskUser(String toolName) {
        return ToolCatalog.ASK_USER.id().equals(toolName);
    }

    private AgentRunOutcome pauseForAskUser(AgentRun run, List<ChatMessage> messages, ToolCall toolCall,
                                              LoopState state) {
        String question = toolCall.arguments() != null
            && toolCall.arguments().get("question") instanceof String value ? value : null;

        finalizeTotals(run, state);
        run.setConversationState(MAPPER.writeValueAsString(messages));
        run.setStatus(RunStatus.AWAITING_USER);
        agentRunRepository.save(run);

        return new AgentRunOutcome(run.getId(), RunStatus.AWAITING_USER, null, question);
    }

    private void finalizeTotals(AgentRun run, LoopState state) {
        int previousTokens = run.getTotalTokens() != null ? run.getTotalTokens() : 0;
        run.setTotalTokens(previousTokens + state.tokensUsed);
        run.setTotalLatencyMs((int) Duration.between(run.getCreatedAt(), clock.instant()).toMillis());
    }

    private static final class LoopState {
        private int stepNumber;
        private int tokensUsed;
        private final Map<String, Integer> repeatCounts = new HashMap<>();
    }
}
