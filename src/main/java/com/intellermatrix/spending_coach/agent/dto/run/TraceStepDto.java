package com.intellermatrix.spending_coach.agent.dto.run;

import java.util.Map;

public record TraceStepDto(int stepNumber, String tool, Map<String, Object> arguments, String resultSummary,
                            Integer latencyMs, Integer tokens, String error) {
}
