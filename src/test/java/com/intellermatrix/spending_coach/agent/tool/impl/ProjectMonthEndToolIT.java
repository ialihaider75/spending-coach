package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.tool.ProjectionResult;
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
class ProjectMonthEndToolIT extends IntegrationTestSupport {

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    private static void save(TransactionRepository transactions, User user, LocalDate date, long amount,
                              Kind kind, String category, String note) {
        transactions.save(new Transaction(UUID.randomUUID(), user.getId(), date, amount, kind, category, note,
            Source.MANUAL));
    }

    @Test
    void findsTheDayTheCumulativeBalanceFirstGoesNegative(@Autowired UserRepository userRepository,
                                                            @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 10, 1), 1000, Kind.INCOME, "salary", "pay");
        save(transactionRepository, user, LocalDate.of(2026, 10, 10), 600, Kind.EXPENSE, "rent", "rent");
        save(transactionRepository, user, LocalDate.of(2026, 10, 20), 500, Kind.EXPENSE, "dining", "spike");

        var tool = new ProjectMonthEndTool(transactionRepository);
        var result = (ProjectionResult) tool.execute(user.getId(), Map.of()).payload();

        assertThat(result.zeroCrossingDay()).isEqualTo(20);
    }

    @Test
    void returnsNullZeroCrossingDayWhenBalanceStaysPositive(@Autowired UserRepository userRepository,
                                                             @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 10, 1), 1000, Kind.INCOME, "salary", "pay");
        save(transactionRepository, user, LocalDate.of(2026, 10, 10), 100, Kind.EXPENSE, "rent", "rent");

        var tool = new ProjectMonthEndTool(transactionRepository);
        var result = (ProjectionResult) tool.execute(user.getId(), Map.of()).payload();

        assertThat(result.zeroCrossingDay()).isNull();
    }

    @Test
    void noTransactionsReturnsAStructuredErrorNotAnException(@Autowired UserRepository userRepository,
                                                               @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        var tool = new ProjectMonthEndTool(transactionRepository);

        var result = tool.execute(user.getId(), Map.of());

        assertThat(result.error()).isNotNull();
        assertThat(result.payload()).isNull();
    }
}
