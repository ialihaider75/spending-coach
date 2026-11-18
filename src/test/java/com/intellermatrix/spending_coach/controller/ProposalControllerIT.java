package com.intellermatrix.spending_coach.controller;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.enums.Status;
import com.intellermatrix.spending_coach.persistence.entity.Budget;
import com.intellermatrix.spending_coach.persistence.entity.BudgetId;
import com.intellermatrix.spending_coach.persistence.entity.BudgetProposal;
import com.intellermatrix.spending_coach.persistence.entity.User;
import com.intellermatrix.spending_coach.persistence.repository.BudgetProposalRepository;
import com.intellermatrix.spending_coach.persistence.repository.BudgetRepository;
import com.intellermatrix.spending_coach.persistence.repository.UserRepository;
import com.intellermatrix.spending_coach.proposal.dto.ProposalDecisionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ProposalControllerIT extends IntegrationTestSupport {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BudgetProposalRepository budgetProposalRepository;
    @Autowired
    private BudgetRepository budgetRepository;

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    private static BudgetProposal aPendingProposal(BudgetProposalRepository proposals, User user, String category,
                                                      long newMonthlyLimit) {
        String changesJson = String.format(Locale.ROOT,
            "[{\"category\":\"%s\",\"newMonthlyLimit\":%d}]", category, newMonthlyLimit);
        return proposals.save(new BudgetProposal(UUID.randomUUID(), user.getId(), changesJson, Status.PENDING, null));
    }

    @Test
    void approvingAProposalUpsertsTheBudget() {
        var user = aSavedUser(userRepository);
        var proposal = aPendingProposal(budgetProposalRepository, user, "dining", 5000);

        var response = rest.postForEntity("/api/proposals/" + proposal.getId() + "/approve",
            null, ProposalDecisionResponse.class).getBody();

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(budgetRepository.findById(new BudgetId(user.getId(), "dining")).orElseThrow().getMonthlyLimit())
            .isEqualTo(5000);
    }

    @Test
    void approvingAProposalUpdatesAnExistingBudget() {
        var user = aSavedUser(userRepository);
        budgetRepository.save(new Budget(user.getId(), "dining", 2000));
        var proposal = aPendingProposal(budgetProposalRepository, user, "dining", 5000);

        rest.postForEntity("/api/proposals/" + proposal.getId() + "/approve", null, ProposalDecisionResponse.class);

        assertThat(budgetRepository.findById(new BudgetId(user.getId(), "dining")).orElseThrow().getMonthlyLimit())
            .isEqualTo(5000);
    }

    @Test
    void rejectingAProposalLeavesBudgetsUnchanged() {
        var user = aSavedUser(userRepository);
        var proposal = aPendingProposal(budgetProposalRepository, user, "dining", 5000);

        var response = rest.postForEntity("/api/proposals/" + proposal.getId() + "/reject",
            null, ProposalDecisionResponse.class).getBody();

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(budgetRepository.findById(new BudgetId(user.getId(), "dining"))).isEmpty();
    }

    @Test
    void approvingAnUnknownProposalReturns404() {
        var response = rest.postForEntity("/api/proposals/" + UUID.randomUUID() + "/approve",
            null, ProposalDecisionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
