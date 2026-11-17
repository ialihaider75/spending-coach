package com.intellermatrix.spending_coach.agent.tool;

public record ToolExecutionResult(String toolName, Object payload, String resultSummary, boolean truncated, String error) {
}
