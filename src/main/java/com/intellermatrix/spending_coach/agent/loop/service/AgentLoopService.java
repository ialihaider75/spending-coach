package com.intellermatrix.spending_coach.agent.loop.service;

import com.intellermatrix.spending_coach.agent.dto.loop.AgentRunOutcome;

import java.util.UUID;

public interface AgentLoopService {

    AgentRunOutcome startRun(UUID userId, String question);

    AgentRunOutcome resumeRun(UUID runId, String answer);
}
