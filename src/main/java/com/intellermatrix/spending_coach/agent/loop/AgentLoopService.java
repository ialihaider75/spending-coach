package com.intellermatrix.spending_coach.agent.loop;

import java.util.UUID;

public interface AgentLoopService {

    AgentRunOutcome startRun(UUID userId, String question);

    AgentRunOutcome resumeRun(UUID runId, String answer);
}
