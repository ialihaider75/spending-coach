package com.intellermatrix.spending_coach.ingestion.service;

import com.intellermatrix.spending_coach.enums.Kind;
import com.intellermatrix.spending_coach.enums.Source;
import com.intellermatrix.spending_coach.ingestion.dto.CsvParseResult;
import com.intellermatrix.spending_coach.persistence.entity.Transaction;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TransactionCsvParser {

    static final String REASON_INVALID_FIELD_COUNT = "row must have 5 fields";
    static final String REASON_INVALID_DATE = "date must be ISO yyyy-MM-dd";
    static final String REASON_AMOUNT_NOT_NUMERIC = "amount must be numeric";
    static final String REASON_AMOUNT_NOT_POSITIVE = "amount must be positive";
    static final String REASON_INVALID_KIND = "kind must be expense or income";
    static final String REASON_CATEGORY_TOO_LONG = "category must be 100 characters or fewer";

    private static final int CATEGORY_MAX_LENGTH = 100;

    public CsvParseResult parse(UUID userId, InputStream csv) {
        List<Transaction> parsed = new ArrayList<>();
        List<CsvParseResult.RejectedRow> rejected = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            var header = reader.readLine();
            if (header == null) {
                return new CsvParseResult(parsed, rejected);
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                parseRow(userId, line, lineNumber, parsed, rejected);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new CsvParseResult(parsed, rejected);
    }

    private void parseRow(UUID userId, String line, int lineNumber, List<Transaction> parsed,
                           List<CsvParseResult.RejectedRow> rejected) {
        String[] fields = line.split(",", -1);
        if (fields.length != 5) {
            rejected.add(new CsvParseResult.RejectedRow(lineNumber, line, REASON_INVALID_FIELD_COUNT));
            return;
        }

        String dateField = fields[0].trim();
        String amountField = fields[1].trim();
        String category = fields[2].trim();
        String note = fields[3].trim();
        String kindField = fields[4].trim();

        if (category.length() > CATEGORY_MAX_LENGTH) {
            rejected.add(new CsvParseResult.RejectedRow(lineNumber, line, REASON_CATEGORY_TOO_LONG));
            return;
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateField);
        } catch (DateTimeParseException e) {
            rejected.add(new CsvParseResult.RejectedRow(lineNumber, line, REASON_INVALID_DATE));
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(amountField);
        } catch (NumberFormatException e) {
            rejected.add(new CsvParseResult.RejectedRow(lineNumber, line, REASON_AMOUNT_NOT_NUMERIC));
            return;
        }
        if (amount <= 0) {
            rejected.add(new CsvParseResult.RejectedRow(lineNumber, line, REASON_AMOUNT_NOT_POSITIVE));
            return;
        }

        Optional<Kind> kind = Kind.fromString(kindField);
        if (kind.isEmpty()) {
            rejected.add(new CsvParseResult.RejectedRow(lineNumber, line, REASON_INVALID_KIND));
            return;
        }

        parsed.add(new Transaction(UUID.randomUUID(), userId, date, amount, kind.get(), category, note, Source.CSV));
    }
}
