package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TopTransactionsToolIT extends IntegrationTestSupport {

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    private static void save(TransactionRepository transactions, User user, LocalDate date, long amount,
                              Kind kind, String category, String note) {
        transactions.save(new Transaction(UUID.randomUUID(), user.getId(), date, amount, kind, category, note,
            Source.MANUAL));
    }

    @Test
    void returnsTheLargestMatchingRowsAndFlagsTruncation(@Autowired UserRepository userRepository,
                                                            @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 10, 1), 500, Kind.EXPENSE, "dining", "a");
        save(transactionRepository, user, LocalDate.of(2026, 10, 2), 1500, Kind.EXPENSE, "dining", "b");
        save(transactionRepository, user, LocalDate.of(2026, 10, 3), 1000, Kind.EXPENSE, "dining", "c");

        var tool = new TopTransactionsTool(transactionRepository);
        var result = tool.execute(user.getId(), Map.of("category", "dining", "limit", 2));

        @SuppressWarnings("unchecked")
        var rows = (List<TransactionRow>) result.payload();
        assertThat(rows).extracting(TransactionRow::amount).containsExactly(1500L, 1000L);
        assertThat(result.truncated()).isTrue();
    }
}
