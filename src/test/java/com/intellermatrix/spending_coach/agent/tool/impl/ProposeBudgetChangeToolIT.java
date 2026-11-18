package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.enums.Status;
import com.intellermatrix.spending_coach.persistence.entity.User;
import com.intellermatrix.spending_coach.persistence.repository.BudgetProposalRepository;
import com.intellermatrix.spending_coach.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProposeBudgetChangeToolIT extends IntegrationTestSupport {

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertsAPendingProposalAndReturnsItsId(@Autowired UserRepository userRepository,
                                                  @Autowired BudgetProposalRepository budgetProposalRepository) {
        var user = aSavedUser(userRepository);
        var tool = new ProposeBudgetChangeTool(budgetProposalRepository);

        var result = tool.execute(user.getId(), Map.of(
            "changes", List.of(Map.of("category", "dining", "newMonthlyLimit", 5000))));

        var proposalId = (UUID) ((Map<String, Object>) result.payload()).get("proposalId");
        var saved = budgetProposalRepository.findById(proposalId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(Status.PENDING);
        assertThat(saved.getChangesJson()).contains("dining").contains("5000");
    }

    @Test
    void aChangeMissingACategoryReturnsAStructuredErrorNotAnException(@Autowired UserRepository userRepository,
                                                                        @Autowired BudgetProposalRepository budgetProposalRepository) {
        var user = aSavedUser(userRepository);
        var tool = new ProposeBudgetChangeTool(budgetProposalRepository);

        var result = tool.execute(user.getId(), Map.of(
            "changes", List.of(Map.of("newMonthlyLimit", 5000))));

        assertThat(result.error()).isNotNull();
        assertThat(result.payload()).isNull();
    }

    @Test
    void aNegativeNewMonthlyLimitReturnsAStructuredErrorNotAWrongResult(@Autowired UserRepository userRepository,
                                                                          @Autowired BudgetProposalRepository budgetProposalRepository) {
        var user = aSavedUser(userRepository);
        var tool = new ProposeBudgetChangeTool(budgetProposalRepository);

        var result = tool.execute(user.getId(), Map.of(
            "changes", List.of(Map.of("category", "dining", "newMonthlyLimit", -1))));

        assertThat(result.error()).isNotNull();
        assertThat(result.payload()).isNull();
    }

    @Test
    void changesThatIsNotAListReturnsAStructuredErrorNotAnException(@Autowired UserRepository userRepository,
                                                                       @Autowired BudgetProposalRepository budgetProposalRepository) {
        var user = aSavedUser(userRepository);
        var tool = new ProposeBudgetChangeTool(budgetProposalRepository);

        var result = tool.execute(user.getId(), Map.of("changes", "not-a-list"));

        assertThat(result.error()).isNotNull();
        assertThat(result.payload()).isNull();
    }

    @Test
    void anEmptyChangesListReturnsAStructuredErrorNotAPointlessProposal(@Autowired UserRepository userRepository,
                                                                           @Autowired BudgetProposalRepository budgetProposalRepository) {
        var user = aSavedUser(userRepository);
        var tool = new ProposeBudgetChangeTool(budgetProposalRepository);

        var result = tool.execute(user.getId(), Map.of("changes", List.of()));

        assertThat(result.error()).isNotNull();
        assertThat(result.payload()).isNull();
    }

    @Test
    void aBlankCategoryReturnsAStructuredErrorNotAWrongResult(@Autowired UserRepository userRepository,
                                                                 @Autowired BudgetProposalRepository budgetProposalRepository) {
        var user = aSavedUser(userRepository);
        var tool = new ProposeBudgetChangeTool(budgetProposalRepository);

        var result = tool.execute(user.getId(), Map.of(
            "changes", List.of(Map.of("category", "   ", "newMonthlyLimit", 5000))));

        assertThat(result.error()).isNotNull();
        assertThat(result.payload()).isNull();
    }

    @Test
    void unserializableChangesReturnsAStructuredErrorNotAnException(@Autowired UserRepository userRepository,
                                                                        @Autowired BudgetProposalRepository budgetProposalRepository) {
        var user = aSavedUser(userRepository);
        var tool = new ProposeBudgetChangeTool(budgetProposalRepository);

        Map<String, Object> selfReferencing = new HashMap<>();
        selfReferencing.put("loop", selfReferencing);
        Map<String, Object> change = new HashMap<>();
        change.put("category", "dining");
        change.put("newMonthlyLimit", 5000);
        change.put("extra", selfReferencing);

        var result = tool.execute(user.getId(), Map.of("changes", List.of(change)));

        assertThat(result.error()).isNotNull();
        assertThat(result.payload()).isNull();
    }
}
