package com.intellermatrix.spending_coach.agent.run.service;

import com.intellermatrix.spending_coach.agent.dto.loop.AgentRunOutcome;
import com.intellermatrix.spending_coach.agent.dto.run.ChatResponse;

import java.util.UUID;

public interface RunQueryService {

    ChatResponse toChatResponse(AgentRunOutcome outcome);

    ChatResponse getRun(UUID runId);
}
