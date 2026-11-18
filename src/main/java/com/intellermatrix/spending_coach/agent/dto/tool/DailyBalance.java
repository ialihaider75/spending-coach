package com.intellermatrix.spending_coach.agent.dto.tool;

import java.time.LocalDate;

public record DailyBalance(LocalDate date, long balance) {
}
