package com.intellermatrix.spending_coach.agent.dto.tool;

public record RecurringCharge(String category, String note, int occurrences, String cadence, long currentAmount, Long previousAmount) {
}
