package com.intellermatrix.spending_coach.agent.loop.service.impl;

import com.intellermatrix.spending_coach.agent.dto.loop.AgentRunOutcome;
import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderRequest;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderResponse;
import com.intellermatrix.spending_coach.agent.dto.provider.ToolCall;
import com.intellermatrix.spending_coach.agent.dto.tool.ToolExecutionResult;
import com.intellermatrix.spending_coach.agent.loop.SystemPromptBuilder;
import com.intellermatrix.spending_coach.agent.loop.service.AgentLoopService;
import com.intellermatrix.spending_coach.agent.provider.LlmProvider;
import com.intellermatrix.spending_coach.agent.tool.ToolRegistry;
import com.intellermatrix.spending_coach.enums.RunStatus;
import com.intellermatrix.spending_coach.persistence.entity.AgentRun;
import com.intellermatrix.spending_coach.persistence.entity.AgentStep;
import com.intellermatrix.spending_coach.persistence.repository.AgentRunRepository;
import com.intellermatrix.spending_coach.persistence.repository.AgentStepRepository;
import com.intellermatrix.spending_coach.persistence.repository.BudgetProposalRepository;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class DefaultAgentLoopService implements AgentLoopService {

    private static final int MAX_STEPS = 8;
    private static final String STEP_LIMIT_ANSWER =
        "Reached the step limit before finishing — here is what was found so far.";
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final LlmProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final BudgetProposalRepository budgetProposalRepository;
    private final Clock clock;

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
        throw new UnsupportedOperationException("resumeRun is not yet implemented");
    }

    private AgentRunOutcome runLoop(AgentRun run, List<ChatMessage> messages) {
        int stepNumber = 0;
        while (true) {
            ProviderResponse response = llmProvider.chat(new ProviderRequest(messages, toolRegistry.definitions()));
            ChatMessage reply = response.message();

            if (reply.toolCalls() == null || reply.toolCalls().isEmpty()) {
                return complete(run, reply.content());
            }

            messages.add(reply);
            for (ToolCall toolCall : reply.toolCalls()) {
                stepNumber++;
                messages.add(dispatch(run, stepNumber, toolCall));
                if (stepNumber >= MAX_STEPS) {
                    return complete(run, STEP_LIMIT_ANSWER);
                }
            }
        }
    }

    private ChatMessage dispatch(AgentRun run, int stepNumber, ToolCall toolCall) {
        long startMillis = clock.millis();
        ToolExecutionResult result = toolRegistry.execute(run.getUserId(), toolCall.toolName(), toolCall.arguments());
        int latencyMs = (int) (clock.millis() - startMillis);

        String argumentsJson = MAPPER.writeValueAsString(toolCall.arguments());
        agentStepRepository.save(new AgentStep(run.getId(), stepNumber, toolCall.toolName(), argumentsJson,
            result.resultSummary(), latencyMs, null, result.error()));

        String content = MAPPER.writeValueAsString(result);
        return new ChatMessage("tool", content, null, toolCall.id());
    }

    private AgentRunOutcome complete(AgentRun run, String answer) {
        run.setStatus(RunStatus.COMPLETED);
        agentRunRepository.save(run);
        return new AgentRunOutcome(run.getId(), RunStatus.COMPLETED, answer, null);
    }
}
