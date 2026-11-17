package com.intellermatrix.spending_coach.agent.tool.impl.dto;

import java.time.LocalDate;

public record IncomeEntry(LocalDate date, long amount, String note) {
}
