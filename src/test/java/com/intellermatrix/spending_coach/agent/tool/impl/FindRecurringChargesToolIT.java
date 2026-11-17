package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.tool.RecurringCharge;
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
class FindRecurringChargesToolIT extends IntegrationTestSupport {

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    private static void save(TransactionRepository transactions, User user, LocalDate date, long amount,
                              Kind kind, String category, String note) {
        transactions.save(new Transaction(UUID.randomUUID(), user.getId(), date, amount, kind, category, note,
            Source.MANUAL));
    }

    @Test
    void groupsRecurringChargesAndReportsTheLatestAmountChange(@Autowired UserRepository userRepository,
                                                                  @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 7, 20), 100, Kind.EXPENSE, "subscription", "monthly subscription");
        save(transactionRepository, user, LocalDate.of(2026, 8, 20), 100, Kind.EXPENSE, "subscription", "monthly subscription");
        save(transactionRepository, user, LocalDate.of(2026, 9, 20), 150, Kind.EXPENSE, "subscription", "monthly subscription");

        var tool = new FindRecurringChargesTool(transactionRepository);
        @SuppressWarnings("unchecked")
        var result = (List<RecurringCharge>) tool.execute(user.getId(), Map.of("minOccurrences", 3)).payload();

        var subscription = result.stream().filter(r -> r.category().equals("subscription")).findFirst().orElseThrow();
        assertThat(subscription.occurrences()).isEqualTo(3);
        assertThat(subscription.cadence()).isEqualTo("monthly");
        assertThat(subscription.currentAmount()).isEqualTo(150);
        assertThat(subscription.previousAmount()).isEqualTo(100);
    }

    @Test
    void aSingleOccurrenceGroupReportsAnUnknownCadenceNotAGuessedOne(@Autowired UserRepository userRepository,
                                                                        @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 10, 5), 1800, Kind.EXPENSE, "repairs", "laptop repair");

        var tool = new FindRecurringChargesTool(transactionRepository);
        @SuppressWarnings("unchecked")
        var result = (List<RecurringCharge>) tool.execute(user.getId(), Map.of("minOccurrences", 1)).payload();

        var repairs = result.stream().filter(r -> r.category().equals("repairs")).findFirst().orElseThrow();
        assertThat(repairs.occurrences()).isEqualTo(1);
        assertThat(repairs.cadence()).isEqualTo("unknown");
    }

    @Test
    void aNegativeMinOccurrencesReturnsAStructuredErrorNotAWrongResult(@Autowired UserRepository userRepository,
                                                                          @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        var tool = new FindRecurringChargesTool(transactionRepository);

        var result = tool.execute(user.getId(), Map.of("minOccurrences", -1));

        assertThat(result.error()).isNotNull();
        assertThat(result.payload()).isNull();
    }
}
