package com.intellermatrix.spending_coach.controller;

import com.intellermatrix.spending_coach.proposal.dto.ProposalDecisionResponse;
import com.intellermatrix.spending_coach.proposal.service.ProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProposalController {

    public static final String APPROVE_ENDPOINT = "/api/proposals/{id}/approve";
    public static final String REJECT_ENDPOINT = "/api/proposals/{id}/reject";

    private final ProposalService proposalService;

    @PostMapping(APPROVE_ENDPOINT)
    public ProposalDecisionResponse approve(@PathVariable UUID id) {
        return proposalService.approve(id);
    }

    @PostMapping(REJECT_ENDPOINT)
    public ProposalDecisionResponse reject(@PathVariable UUID id) {
        return proposalService.reject(id);
    }
}
