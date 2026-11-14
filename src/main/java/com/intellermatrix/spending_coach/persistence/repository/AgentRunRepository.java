package com.intellermatrix.spending_coach.persistence.repository;

import com.intellermatrix.spending_coach.persistence.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
}
