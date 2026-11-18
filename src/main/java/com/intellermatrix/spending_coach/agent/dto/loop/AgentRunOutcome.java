package com.intellermatrix.spending_coach.agent.dto.loop;

import com.intellermatrix.spending_coach.enums.RunStatus;

import java.util.UUID;

public record AgentRunOutcome(UUID runId, RunStatus status, String answer, String pendingQuestion) {
}
