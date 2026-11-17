package com.intellermatrix.spending_coach.agent.dto.tool;

public record PeriodComparisonRow(String category, long amountA, long monthlyAverageB, long change, double changePercent) {
}
