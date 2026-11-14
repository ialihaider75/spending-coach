package com.intellermatrix.spending_coach.persistence.repository;

import com.intellermatrix.spending_coach.persistence.entity.Budget;
import com.intellermatrix.spending_coach.persistence.entity.BudgetId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, BudgetId> {
}
