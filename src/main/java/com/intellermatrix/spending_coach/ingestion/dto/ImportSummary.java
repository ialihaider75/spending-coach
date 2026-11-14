package com.intellermatrix.spending_coach.ingestion.dto;

import java.util.List;

public record ImportSummary(int importedCount, int rejectedCount, List<String> rejectionReasons) {
}
