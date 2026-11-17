package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.tool.OverviewResult;
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
class GetOverviewToolIT extends IntegrationTestSupport {

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    private static void save(TransactionRepository transactions, User user, LocalDate date, long amount,
                              Kind kind, String category, String note) {
        transactions.save(new Transaction(UUID.randomUUID(), user.getId(), date, amount, kind, category, note,
            Source.MANUAL));
    }

    @Test
    void summarizesTransactionsForTheGivenUserOnly(@Autowired UserRepository userRepository,
                                                     @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);
        var otherUser = aSavedUser(userRepository);
        save(transactionRepository, user, LocalDate.of(2026, 10, 1), 100000, Kind.INCOME, "salary", "pay");
        save(transactionRepository, user, LocalDate.of(2026, 10, 5), 3000, Kind.EXPENSE, "groceries", "shop");
        save(transactionRepository, otherUser, LocalDate.of(2026, 10, 5), 999999, Kind.EXPENSE, "groceries", "not mine");

        var tool = new GetOverviewTool(transactionRepository);
        var result = (OverviewResult) tool.execute(user.getId(), Map.of()).payload();

        assertThat(result.transactionCount()).isEqualTo(2);
        assertThat(result.categories()).containsExactlyInAnyOrder("salary", "groceries");
        assertThat(result.fromDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.toDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(result.incomeEntries()).hasSize(1);
    }

    @Test
    void aUserWithNoTransactionsGetsAnEmptyOverviewNotAnException(@Autowired UserRepository userRepository,
                                                                      @Autowired TransactionRepository transactionRepository) {
        var user = aSavedUser(userRepository);

        var tool = new GetOverviewTool(transactionRepository);
        var result = (OverviewResult) tool.execute(user.getId(), Map.of()).payload();

        assertThat(result.transactionCount()).isZero();
        assertThat(result.fromDate()).isNull();
        assertThat(result.toDate()).isNull();
        assertThat(result.categories()).isEmpty();
        assertThat(result.incomeEntries()).isEmpty();
        assertThat(result.monthlyTotals()).isEmpty();
    }
}
