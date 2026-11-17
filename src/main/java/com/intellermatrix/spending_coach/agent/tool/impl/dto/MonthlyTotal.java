package com.intellermatrix.spending_coach.agent.tool.impl.dto;

import java.time.YearMonth;

public record MonthlyTotal(YearMonth month, long income, long expense) {
}
