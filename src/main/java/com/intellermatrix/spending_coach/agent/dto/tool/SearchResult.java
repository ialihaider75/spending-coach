package com.intellermatrix.spending_coach.agent.dto.tool;

import java.util.List;

public record SearchResult(List<TransactionRow> rows, long totalCount) {
}
