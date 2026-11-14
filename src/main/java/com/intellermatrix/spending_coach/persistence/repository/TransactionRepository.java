package com.intellermatrix.spending_coach.persistence.repository;

import com.intellermatrix.spending_coach.persistence.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
}
