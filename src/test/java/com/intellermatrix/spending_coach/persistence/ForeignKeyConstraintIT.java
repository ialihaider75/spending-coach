package com.intellermatrix.spending_coach.persistence;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.enums.Kind;
import com.intellermatrix.spending_coach.enums.RunStatus;
import com.intellermatrix.spending_coach.enums.Source;
import com.intellermatrix.spending_coach.enums.Status;
import com.intellermatrix.spending_coach.persistence.entity.AgentRun;
import com.intellermatrix.spending_coach.persistence.entity.AgentStep;
import com.intellermatrix.spending_coach.persistence.entity.Budget;
import com.intellermatrix.spending_coach.persistence.entity.BudgetProposal;
import com.intellermatrix.spending_coach.persistence.entity.Transaction;
import com.intellermatrix.spending_coach.persistence.entity.User;
import com.intellermatrix.spending_coach.persistence.repository.AgentRunRepository;
import com.intellermatrix.spending_coach.persistence.repository.AgentStepRepository;
import com.intellermatrix.spending_coach.persistence.repository.BudgetProposalRepository;
import com.intellermatrix.spending_coach.persistence.repository.BudgetRepository;
import com.intellermatrix.spending_coach.persistence.repository.TransactionRepository;
import com.intellermatrix.spending_coach.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ForeignKeyConstraintIT extends IntegrationTestSupport {

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    @Test
    void transactionWithUnknownUserIdIsRejectedByTheDatabase(@Autowired TransactionRepository transactions,
                                                               @Autowired UserRepository users) {
        User saved = aSavedUser(users);
        Transaction t = new Transaction(UUID.randomUUID(), saved.getId(), LocalDate.now(), 1000,
            Kind.EXPENSE, "groceries", "note", Source.MANUAL);
        assertThat(transactions.save(t)).isNotNull();

        Transaction orphan = new Transaction(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
            1000, Kind.EXPENSE, "groceries", "note", Source.MANUAL);
        assertThatThrownBy(() -> transactions.saveAndFlush(orphan))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void budgetWithUnknownUserIdIsRejectedByTheDatabase(@Autowired BudgetRepository budgets) {
        Budget orphan = new Budget(UUID.randomUUID(), "groceries", 50000);

        assertThatThrownBy(() -> budgets.saveAndFlush(orphan))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void budgetProposalWithUnknownUserIdIsRejectedByTheDatabase(@Autowired BudgetProposalRepository proposals) {
        BudgetProposal orphan = new BudgetProposal(UUID.randomUUID(), UUID.randomUUID(), "{}", Status.PENDING, null);

        assertThatThrownBy(() -> proposals.saveAndFlush(orphan))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void budgetProposalWithUnknownRunIdIsRejectedByTheDatabase(@Autowired BudgetProposalRepository proposals,
                                                                 @Autowired UserRepository users) {
        User saved = aSavedUser(users);
        BudgetProposal orphan = new BudgetProposal(UUID.randomUUID(), saved.getId(), "{}", Status.PENDING,
            UUID.randomUUID());

        assertThatThrownBy(() -> proposals.saveAndFlush(orphan))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void agentRunWithUnknownUserIdIsRejectedByTheDatabase(@Autowired AgentRunRepository agentRuns) {
        AgentRun orphan = new AgentRun(UUID.randomUUID(), UUID.randomUUID(), "How much did I spend on dining?",
            "mock", RunStatus.RUNNING, null, null, Instant.now());

        assertThatThrownBy(() -> agentRuns.saveAndFlush(orphan))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void agentStepWithUnknownRunIdIsRejectedByTheDatabase(@Autowired AgentStepRepository agentSteps) {
        AgentStep orphan = new AgentStep(UUID.randomUUID(), 1, "top_transactions", "{}", null, null, null, null);

        assertThatThrownBy(() -> agentSteps.saveAndFlush(orphan))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
