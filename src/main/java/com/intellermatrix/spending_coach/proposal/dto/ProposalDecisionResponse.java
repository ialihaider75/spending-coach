package com.intellermatrix.spending_coach.proposal.dto;

import java.util.UUID;

public record ProposalDecisionResponse(UUID proposalId, String status) {
}
