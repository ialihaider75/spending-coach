package com.intellermatrix.spending_coach.ingestion.dto;

import com.intellermatrix.spending_coach.persistence.entity.Transaction;

import java.util.List;

public record CsvParseResult(List<Transaction> parsed, List<RejectedRow> rejected) {

    public record RejectedRow(int lineNumber, String rawLine, String reason) {
    }
}
