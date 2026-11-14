package com.intellermatrix.spending_coach.generator;

import com.intellermatrix.spending_coach.ingestion.service.TransactionCsvParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticDataGeneratorTest {

    private final SyntheticDataGenerator generator = new SyntheticDataGenerator();
    private final TransactionCsvParser csvParser = new TransactionCsvParser();
    private final UUID userId = UUID.randomUUID();

    private static InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void sameSeedAndAnchorProduceIdenticalOutput() {
        String a = generator.generateCsv(12345L, LocalDate.of(2026, 10, 31));
        String b = generator.generateCsv(12345L, LocalDate.of(2026, 10, 31));
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentSeedsProduceDifferentSpecificsSameShape() {
        String a = generator.generateCsv(1L, LocalDate.of(2026, 10, 31));
        String b = generator.generateCsv(2L, LocalDate.of(2026, 10, 31));
        assertThat(a).isNotEqualTo(b);
        assertThat(csvParser.parse(userId, toStream(a)).rejected()).isEmpty();
        assertThat(csvParser.parse(userId, toStream(b)).rejected()).isEmpty();
    }

    @Test
    void generatedCsvParsesCleanlyThroughTheRealImportParser() {
        String csv = generator.generateCsv(12345L, LocalDate.of(2026, 10, 31));
        var result = csvParser.parse(userId, toStream(csv));
        assertThat(result.rejected()).isEmpty();
        assertThat(result.parsed()).isNotEmpty();
    }
}
