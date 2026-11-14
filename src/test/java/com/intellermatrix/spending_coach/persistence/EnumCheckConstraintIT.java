package com.intellermatrix.spending_coach.persistence;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.enums.Kind;
import com.intellermatrix.spending_coach.enums.RunStatus;
import com.intellermatrix.spending_coach.enums.Source;
import com.intellermatrix.spending_coach.enums.Status;
import com.intellermatrix.spending_coach.persistence.entity.AgentRun;
import com.intellermatrix.spending_coach.persistence.entity.BudgetProposal;
import com.intellermatrix.spending_coach.persistence.entity.Transaction;
import com.intellermatrix.spending_coach.persistence.entity.User;
import com.intellermatrix.spending_coach.persistence.repository.AgentRunRepository;
import com.intellermatrix.spending_coach.persistence.repository.BudgetProposalRepository;
import com.intellermatrix.spending_coach.persistence.repository.TransactionRepository;
import com.intellermatrix.spending_coach.persistence.repository.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EnumCheckConstraintIT extends IntegrationTestSupport {

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void everyKindValueSatisfiesTheCheckConstraint(Kind kind, @Autowired TransactionRepository transactions,
                                                     @Autowired UserRepository users) {
        User user = aSavedUser(users);
        Transaction t = new Transaction(UUID.randomUUID(), user.getId(), LocalDate.now(), 1000,
            kind, "groceries", "note", Source.MANUAL);

        assertThat(transactions.saveAndFlush(t).getKind()).isEqualTo(kind);
    }

    @ParameterizedTest
    @EnumSource(Source.class)
    void everySourceValueSatisfiesTheCheckConstraint(Source source, @Autowired TransactionRepository transactions,
                                                       @Autowired UserRepository users) {
        User user = aSavedUser(users);
        Transaction t = new Transaction(UUID.randomUUID(), user.getId(), LocalDate.now(), 1000,
            Kind.EXPENSE, "groceries", "note", source);

        assertThat(transactions.saveAndFlush(t).getSource()).isEqualTo(source);
    }

    @ParameterizedTest
    @EnumSource(Status.class)
    void everyStatusValueSatisfiesTheCheckConstraint(Status status, @Autowired BudgetProposalRepository proposals,
                                                       @Autowired UserRepository users) {
        User user = aSavedUser(users);
        BudgetProposal proposal = new BudgetProposal(UUID.randomUUID(), user.getId(), "{}", status, null);

        assertThat(proposals.saveAndFlush(proposal).getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(RunStatus.class)
    void everyRunStatusValueSatisfiesTheCheckConstraint(RunStatus status, @Autowired AgentRunRepository agentRuns,
                                                          @Autowired UserRepository users) {
        User user = aSavedUser(users);
        AgentRun run = new AgentRun(UUID.randomUUID(), user.getId(), "How much did I spend on dining?",
            "mock", status, null, null, Instant.now());

        assertThat(agentRuns.saveAndFlush(run).getStatus()).isEqualTo(status);
    }
}
