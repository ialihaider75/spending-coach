package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.tool.AnomalyCandidate;
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
class DetectAnomaliesToolIT extends IntegrationTestSupport {

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    private static void save(TransactionRepository transactions, User user, LocalDate date, long amount,
                              Kind kind, String category, String note) {
        transactions.save(new Transaction(UUID.randomUUID(), user.getId(), date, amount, kind, category, note,
            Source.MANUAL));
    }

    @SuppressWarnings("unchecked")
    private static List<AnomalyCandidate> run(DetectAnomaliesTool tool, UUID userId) {
        return (List<AnomalyCandidate>) tool.execute(userId, Map.of()).payload();
    }

    @Test
    void flagsTwoIdenticalSameDayChargesAsDuplicate(@Autowired UserRepository userRepository,
                                                        @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 5, 12), 4367, Kind.EXPENSE, "shopping", "purchase");
        save(transactionRepository, user, LocalDate.of(2026, 5, 12), 4367, Kind.EXPENSE, "shopping", "purchase");

        var tool = new DetectAnomaliesTool(transactionRepository);
        var result = run(tool, user.getId());

        assertThat(result).anyMatch(a -> a.reason().equals("duplicate") && a.amount() == 4367);
    }

    @Test
    void flagsAnAmountIncreaseWithinARecurringGroupAsPriceChange(@Autowired UserRepository userRepository,
                                                                     @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 8, 20), 1024, Kind.EXPENSE, "subscription", "monthly subscription");
        save(transactionRepository, user, LocalDate.of(2026, 9, 20), 1364, Kind.EXPENSE, "subscription", "monthly subscription");

        var tool = new DetectAnomaliesTool(transactionRepository);
        var result = run(tool, user.getId());

        assertThat(result).anyMatch(a -> a.reason().equals("price_change") && a.category().equals("subscription"));
    }

    @Test
    void flagsAMonthWellAboveItsPriorThreeMonthAverageAsASpike(@Autowired UserRepository userRepository,
                                                                   @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 7, 8), 4621, Kind.EXPENSE, "dining", "dinner");
        save(transactionRepository, user, LocalDate.of(2026, 8, 8), 4621, Kind.EXPENSE, "dining", "dinner");
        save(transactionRepository, user, LocalDate.of(2026, 9, 8), 4621, Kind.EXPENSE, "dining", "dinner");
        save(transactionRepository, user, LocalDate.of(2026, 10, 10), 12818, Kind.EXPENSE, "dining", "delivery spree");

        var tool = new DetectAnomaliesTool(transactionRepository);
        var result = run(tool, user.getId());

        assertThat(result).anyMatch(a -> a.reason().equals("spike") && a.category().equals("dining"));
    }

    @Test
    void aFromOnlyRangeExcludesTransactionsBeforeIt(@Autowired UserRepository userRepository,
                                                        @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 5, 12), 4367, Kind.EXPENSE, "shopping", "before range");
        save(transactionRepository, user, LocalDate.of(2026, 5, 12), 4367, Kind.EXPENSE, "shopping", "before range");

        var tool = new DetectAnomaliesTool(transactionRepository);
        @SuppressWarnings("unchecked")
        var result = (List<AnomalyCandidate>) tool.execute(user.getId(), Map.of("from", "2026-06-01")).payload();

        assertThat(result).noneMatch(a -> a.reason().equals("duplicate"));
    }
}
