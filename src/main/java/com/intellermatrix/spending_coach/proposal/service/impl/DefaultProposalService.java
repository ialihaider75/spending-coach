package com.intellermatrix.spending_coach.proposal.service.impl;

import com.intellermatrix.spending_coach.enums.Status;
import com.intellermatrix.spending_coach.persistence.entity.Budget;
import com.intellermatrix.spending_coach.persistence.entity.BudgetId;
import com.intellermatrix.spending_coach.persistence.entity.BudgetProposal;
import com.intellermatrix.spending_coach.persistence.repository.BudgetProposalRepository;
import com.intellermatrix.spending_coach.persistence.repository.BudgetRepository;
import com.intellermatrix.spending_coach.proposal.dto.ProposalDecisionResponse;
import com.intellermatrix.spending_coach.proposal.service.ProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultProposalService implements ProposalService {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final BudgetProposalRepository budgetProposalRepository;
    private final BudgetRepository budgetRepository;

    @Override
    public ProposalDecisionResponse approve(UUID proposalId) {
        BudgetProposal proposal = budgetProposalRepository.findById(proposalId).orElseThrow();

        List<Map<String, Object>> changes = MAPPER.readValue(proposal.getChangesJson(),
            new TypeReference<List<Map<String, Object>>>() { });
        for (Map<String, Object> change : changes) {
            String category = (String) change.get("category");
            long newMonthlyLimit = ((Number) change.get("newMonthlyLimit")).longValue();
            Budget budget = budgetRepository.findById(new BudgetId(proposal.getUserId(), category))
                .orElseGet(() -> new Budget(proposal.getUserId(), category, 0));
            budget.setMonthlyLimit(newMonthlyLimit);
            budgetRepository.save(budget);
        }

        return decide(proposal, Status.APPROVED);
    }

    @Override
    public ProposalDecisionResponse reject(UUID proposalId) {
        BudgetProposal proposal = budgetProposalRepository.findById(proposalId).orElseThrow();
        return decide(proposal, Status.REJECTED);
    }

    private ProposalDecisionResponse decide(BudgetProposal proposal, Status status) {
        proposal.setStatus(status);
        budgetProposalRepository.save(proposal);
        return new ProposalDecisionResponse(proposal.getId(), proposal.getStatus().name());
    }
}
