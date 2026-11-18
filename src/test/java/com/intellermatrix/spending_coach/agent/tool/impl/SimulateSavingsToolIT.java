package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.tool.SimulationResult;
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
class SimulateSavingsToolIT extends IntegrationTestSupport {

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    private static void save(TransactionRepository transactions, User user, LocalDate date, long amount,
                              Kind kind, String category, String note) {
        transactions.save(new Transaction(UUID.randomUUID(), user.getId(), date, amount, kind, category, note,
            Source.MANUAL));
    }

    @Test
    void projectsBalanceAfterApplyingACategoryCut(@Autowired UserRepository userRepository,
                                                    @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 10, 1), 1000, Kind.INCOME, "salary", "pay");
        save(transactionRepository, user, LocalDate.of(2026, 10, 10), 1200, Kind.EXPENSE, "dining", "spree");

        var tool = new SimulateSavingsTool(transactionRepository);
        var result = (SimulationResult) tool.execute(user.getId(), Map.of(
            "adjustments", List.of(Map.of("category", "dining", "cutPercent", 50)))).payload();

        assertThat(result.baselineBalance()).isEqualTo(-200);
        assertThat(result.projectedSavings()).isEqualTo(600);
        assertThat(result.projectedBalance()).isEqualTo(400);
    }

    @Test
    void anOutOfRangeCutPercentReturnsAStructuredErrorNotAWrongResult(@Autowired UserRepository userRepository,
                                                                        @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 10, 1), 1000, Kind.INCOME, "salary", "pay");
        save(transactionRepository, user, LocalDate.of(2026, 10, 10), 1200, Kind.EXPENSE, "dining", "spree");

        var tool = new SimulateSavingsTool(transactionRepository);
        var result = tool.execute(user.getId(), Map.of(
            "adjustments", List.of(Map.of("category", "dining", "cutPercent", 150))));

        assertThat(result.error()).isNotNull();
        assertThat(result.payload()).isNull();
    }

    @Test
    void noTransactionsReturnsAStructuredErrorNotAnException(@Autowired UserRepository userRepository,
                                                               @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        var tool = new SimulateSavingsTool(transactionRepository);

        var result = tool.execute(user.getId(), Map.of("adjustments", List.of()));

        assertThat(result.error()).isNotNull();
        assertThat(result.payload()).isNull();
    }
}
