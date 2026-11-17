package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.tool.PeriodComparisonRow;
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
class ComparePeriodsToolIT extends IntegrationTestSupport {

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    private static void save(TransactionRepository transactions, User user, LocalDate date, long amount,
                              Kind kind, String category, String note) {
        transactions.save(new Transaction(UUID.randomUUID(), user.getId(), date, amount, kind, category, note,
            Source.MANUAL));
    }

    @Test
    void comparesAPeriodAgainstTheMonthlyAverageOfALongerPeriod(@Autowired UserRepository userRepository,
                                                                    @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 10, 10), 1200, Kind.EXPENSE, "dining", "october spike");
        save(transactionRepository, user, LocalDate.of(2026, 7, 10), 400, Kind.EXPENSE, "dining", "july");
        save(transactionRepository, user, LocalDate.of(2026, 8, 10), 400, Kind.EXPENSE, "dining", "august");
        save(transactionRepository, user, LocalDate.of(2026, 9, 10), 400, Kind.EXPENSE, "dining", "september");

        var tool = new ComparePeriodsTool(transactionRepository);
        @SuppressWarnings("unchecked")
        var result = (List<PeriodComparisonRow>) tool.execute(user.getId(), Map.of(
            "periodAFrom", "2026-10-01", "periodATo", "2026-10-31",
            "periodBFrom", "2026-07-01", "periodBTo", "2026-09-30")).payload();

        var dining = result.stream().filter(r -> r.category().equals("dining")).findFirst().orElseThrow();
        assertThat(dining.amountA()).isEqualTo(1200);
        assertThat(dining.monthlyAverageB()).isEqualTo(400);
        assertThat(dining.change()).isEqualTo(800);
    }
}
