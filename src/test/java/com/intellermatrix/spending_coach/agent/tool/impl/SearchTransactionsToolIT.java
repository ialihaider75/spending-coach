package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.tool.SearchResult;
import com.intellermatrix.spending_coach.agent.dto.tool.TransactionRow;
import com.intellermatrix.spending_coach.enums.Kind;
import com.intellermatrix.spending_coach.enums.Source;
import com.intellermatrix.spending_coach.persistence.entity.Transaction;
import com.intellermatrix.spending_coach.persistence.entity.User;
import com.intellermatrix.spending_coach.persistence.repository.TransactionRepository;
import com.intellermatrix.spending_coach.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SearchTransactionsToolIT extends IntegrationTestSupport {

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    private static void save(TransactionRepository transactions, User user, LocalDate date, long amount,
                              Kind kind, String category, String note) {
        transactions.save(new Transaction(UUID.randomUUID(), user.getId(), date, amount, kind, category, note,
            Source.MANUAL));
    }

    @Test
    void matchesNoteTextCaseInsensitivelyAndReportsTotalCount(@Autowired UserRepository userRepository,
                                                                 @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 10, 1), 500, Kind.EXPENSE, "transport", "Careem ride");
        save(transactionRepository, user, LocalDate.of(2026, 10, 2), 3000, Kind.EXPENSE, "groceries", "weekly groceries");

        var tool = new SearchTransactionsTool(transactionRepository);
        var result = (SearchResult) tool.execute(user.getId(), Map.of("text", "careem")).payload();

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.rows()).extracting(TransactionRow::note).containsExactly("Careem ride");
    }
}
