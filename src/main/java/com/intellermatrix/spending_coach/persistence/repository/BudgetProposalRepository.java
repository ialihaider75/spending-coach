package com.intellermatrix.spending_coach.persistence.repository;

import com.intellermatrix.spending_coach.persistence.entity.BudgetProposal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BudgetProposalRepository extends JpaRepository<BudgetProposal, UUID> {
}
