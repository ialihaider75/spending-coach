package com.intellermatrix.spending_coach.generator.models;

import java.time.LocalDate;
import java.util.Locale;

public record TransactionLine(LocalDate date, long amount, String category, String note, String kind) {

    public String toCsvLine() {
        return String.format(Locale.ROOT, "%s,%d,%s,%s,%s", date, amount, category, note, kind);
    }
}
