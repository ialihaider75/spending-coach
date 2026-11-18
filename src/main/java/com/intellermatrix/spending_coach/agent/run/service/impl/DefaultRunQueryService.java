package com.intellermatrix.spending_coach.agent.run.service.impl;

import com.intellermatrix.spending_coach.agent.dto.loop.AgentRunOutcome;
import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.run.ChatResponse;
import com.intellermatrix.spending_coach.agent.dto.run.TraceStepDto;
import com.intellermatrix.spending_coach.agent.run.service.RunQueryService;
import com.intellermatrix.spending_coach.agent.util.PendingAskUserCall;
import com.intellermatrix.spending_coach.enums.RunStatus;
import com.intellermatrix.spending_coach.persistence.entity.AgentRun;
import com.intellermatrix.spending_coach.persistence.entity.AgentStep;
import com.intellermatrix.spending_coach.persistence.repository.AgentRunRepository;
import com.intellermatrix.spending_coach.persistence.repository.AgentStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultRunQueryService implements RunQueryService {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;

    @Override
    public ChatResponse toChatResponse(AgentRunOutcome outcome) {
        return new ChatResponse(outcome.runId(), outcome.status().name(), outcome.answer(),
            outcome.pendingQuestion(), trace(outcome.runId()));
    }

    @Override
    public ChatResponse getRun(UUID runId) {
        AgentRun run = agentRunRepository.findById(runId).orElseThrow();
        String pendingQuestion = run.getStatus() == RunStatus.AWAITING_USER
            ? pendingQuestion(run.getConversationState())
            : null;
        return new ChatResponse(run.getId(), run.getStatus().name(), null, pendingQuestion, trace(runId));
    }

    private String pendingQuestion(String conversationStateJson) {
        List<ChatMessage> messages = MAPPER.readValue(conversationStateJson, new TypeReference<List<ChatMessage>>() { });
        return PendingAskUserCall.findIn(messages)
            .map(toolCall -> toolCall.arguments() != null
                && toolCall.arguments().get("question") instanceof String question ? question : null)
            .orElse(null);
    }

    private List<TraceStepDto> trace(UUID runId) {
        return agentStepRepository.findByRunId(runId).stream()
            .sorted(Comparator.comparingInt(AgentStep::getStepNumber))
            .map(this::toTraceStepDto)
            .toList();
    }

    private TraceStepDto toTraceStepDto(AgentStep step) {
        Map<String, Object> arguments = step.getArgumentsJson() != null
            ? MAPPER.readValue(step.getArgumentsJson(), new TypeReference<Map<String, Object>>() { })
            : Map.of();
        return new TraceStepDto(step.getStepNumber(), step.getTool(), arguments, step.getResultSummary(),
            step.getLatencyMs(), step.getTokens(), step.getError());
    }
}
