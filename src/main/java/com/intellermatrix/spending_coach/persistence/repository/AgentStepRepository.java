package com.intellermatrix.spending_coach.persistence.repository;

import com.intellermatrix.spending_coach.persistence.entity.AgentStep;
import com.intellermatrix.spending_coach.persistence.entity.AgentStepId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentStepRepository extends JpaRepository<AgentStep, AgentStepId> {
}
