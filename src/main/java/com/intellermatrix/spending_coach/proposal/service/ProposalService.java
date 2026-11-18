package com.intellermatrix.spending_coach.proposal.service;

import com.intellermatrix.spending_coach.proposal.dto.ProposalDecisionResponse;

import java.util.UUID;

public interface ProposalService {

    ProposalDecisionResponse approve(UUID proposalId);

    ProposalDecisionResponse reject(UUID proposalId);
}
