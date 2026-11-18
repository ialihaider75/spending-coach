package com.intellermatrix.spending_coach.agent.dto.run;

import java.util.List;
import java.util.UUID;

public record ChatResponse(UUID runId, String status, String answer, String pendingQuestion,
                            List<TraceStepDto> trace) {
}
