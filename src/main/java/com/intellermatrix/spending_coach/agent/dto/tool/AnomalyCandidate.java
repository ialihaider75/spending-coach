package com.intellermatrix.spending_coach.agent.dto.tool;

import java.time.LocalDate;
import java.util.UUID;

public record AnomalyCandidate(String reason, UUID transactionId, LocalDate date, long amount, String category, String note) {
}
