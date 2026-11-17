package com.intellermatrix.spending_coach.agent.dto.tool;

import com.intellermatrix.spending_coach.enums.Kind;
import com.intellermatrix.spending_coach.persistence.entity.Transaction;

import java.time.LocalDate;
import java.util.UUID;

public record TransactionRow(UUID id, LocalDate date, long amount, String category, String note, Kind kind) {

    public static TransactionRow from(Transaction t) {
        return new TransactionRow(t.getId(), t.getDate(), t.getAmount(), t.getCategory(), t.getNote(), t.getKind());
    }
}
