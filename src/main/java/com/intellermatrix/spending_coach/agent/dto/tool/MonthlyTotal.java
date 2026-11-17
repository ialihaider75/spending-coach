package com.intellermatrix.spending_coach.agent.dto.tool;

import java.time.YearMonth;

public record MonthlyTotal(YearMonth month, long income, long expense) {
}
