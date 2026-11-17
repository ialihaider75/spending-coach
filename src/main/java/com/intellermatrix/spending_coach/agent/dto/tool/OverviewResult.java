package com.intellermatrix.spending_coach.agent.dto.tool;

import java.time.LocalDate;
import java.util.List;

public record OverviewResult(LocalDate fromDate, LocalDate toDate, long transactionCount, List<String> categories,
                              List<IncomeEntry> incomeEntries, List<MonthlyTotal> monthlyTotals) {
}
