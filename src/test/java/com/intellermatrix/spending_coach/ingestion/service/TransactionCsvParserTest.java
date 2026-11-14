package com.intellermatrix.spending_coach.ingestion.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionCsvParserTest {

    private final TransactionCsvParser parser = new TransactionCsvParser();
    private final UUID userId = UUID.randomUUID();

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsNonNumericAmount() {
        var result = parser.parse(userId, csv("date,amount,category,note,kind\n2026-10-01,abc,groceries,x,expense\n"));
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().getFirst().reason()).contains("amount");
    }

    @Test
    void rejectsNonPositiveAmount() {
        var result = parser.parse(userId, csv("date,amount,category,note,kind\n2026-10-01,0,groceries,x,expense\n"));
        assertThat(result.rejected().getFirst().reason()).isEqualTo(TransactionCsvParser.REASON_AMOUNT_NOT_POSITIVE);
    }

    @Test
    void rejectsUnparseableDate() {
        var result = parser.parse(userId, csv("date,amount,category,note,kind\nnot-a-date,1000,groceries,x,expense\n"));
        assertThat(result.rejected()).hasSize(1);
    }

    @Test
    void emptyFileProducesNoRowsNotAnError() {
        var result = parser.parse(userId, csv("date,amount,category,note,kind\n"));
        assertThat(result.parsed()).isEmpty();
        assertThat(result.rejected()).isEmpty();
    }

    @Test
    void parsesValidRow() {
        var result = parser.parse(userId, csv("date,amount,category,note,kind\n2026-10-01,120000,salary,Employer,income\n"));
        assertThat(result.parsed()).hasSize(1);
        assertThat(result.parsed().getFirst().getAmount()).isEqualTo(120000);
    }
}
