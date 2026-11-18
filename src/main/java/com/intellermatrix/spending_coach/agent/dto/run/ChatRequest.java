package com.intellermatrix.spending_coach.agent.dto.run;

import java.util.UUID;

public record ChatRequest(UUID runId, String question, String answer) {
}
