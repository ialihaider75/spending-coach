package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.tool.CategoryBreakdown;
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
class SpendingByCategoryToolIT extends IntegrationTestSupport {

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    private static void save(TransactionRepository transactions, User user, LocalDate date, long amount,
                              Kind kind, String category, String note) {
        transactions.save(new Transaction(UUID.randomUUID(), user.getId(), date, amount, kind, category, note,
            Source.MANUAL));
    }

    @Test
    void computesTotalAndShareForEachCategory(@Autowired UserRepository userRepository,
                                                @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 10, 5), 3000, Kind.EXPENSE, "groceries", "a");
        save(transactionRepository, user, LocalDate.of(2026, 10, 6), 1000, Kind.EXPENSE, "dining", "b");

        var tool = new SpendingByCategoryTool(transactionRepository);
        @SuppressWarnings("unchecked")
        var result = (List<CategoryBreakdown>) tool.execute(user.getId(),
            Map.of("from", "2026-10-01", "to", "2026-10-31")).payload();

        var groceries = result.stream().filter(r -> r.category().equals("groceries")).findFirst().orElseThrow();
        assertThat(groceries.total()).isEqualTo(3000);
        assertThat(groceries.sharePercent()).isEqualTo(75.0);
    }

    @Test
    void returnsAnEmptyBreakdownWhenNoExpensesFallInTheRange(@Autowired UserRepository userRepository,
                                                                @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 9, 5), 3000, Kind.EXPENSE, "groceries", "outside range");

        var tool = new SpendingByCategoryTool(transactionRepository);
        var result = tool.execute(user.getId(), Map.of("from", "2026-10-01", "to", "2026-10-31"));

        @SuppressWarnings("unchecked")
        var breakdown = (List<CategoryBreakdown>) result.payload();
        assertThat(breakdown).isEmpty();
        assertThat(result.error()).isNull();
    }

    @Test
    void anUnparseableDateReturnsAStructuredErrorNotAnException(@Autowired UserRepository userRepository,
                                                                   @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        var tool = new SpendingByCategoryTool(transactionRepository);

        var result = tool.execute(user.getId(), Map.of("from", "not-a-date", "to", "2026-10-31"));

        assertThat(result.error()).isNotNull();
        assertThat(result.payload()).isNull();
    }
}
