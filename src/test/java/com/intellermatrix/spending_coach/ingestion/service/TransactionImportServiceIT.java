package com.intellermatrix.spending_coach.ingestion.service;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.persistence.entity.User;
import com.intellermatrix.spending_coach.persistence.repository.TransactionRepository;
import com.intellermatrix.spending_coach.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TransactionImportServiceIT extends IntegrationTestSupport {

    @Autowired
    private TransactionImportService service;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importSavesParsedRowsAndSummarizesRejections() {
        User user = userRepository.save(new User(UUID.randomUUID(), "Test User", Instant.now()));

        var summary = service.importCsv(user.getId(), csv("""
            date,amount,category,note,kind
            2026-10-01,120000,salary,Employer,income
            2026-10-02,0,groceries,bad,expense
            """));
        assertThat(summary.importedCount()).isEqualTo(1);
        assertThat(summary.rejectedCount()).isEqualTo(1);
        assertThat(transactionRepository.findAll().stream().filter(t -> t.getUserId().equals(user.getId())))
            .hasSize(1);
    }
}
