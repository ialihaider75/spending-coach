package com.intellermatrix.spending_coach.agent.dto.tool;

import java.time.LocalDate;

public record IncomeEntry(LocalDate date, long amount, String note) {
}
