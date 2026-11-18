package com.intellermatrix.spending_coach.controller;

import com.intellermatrix.spending_coach.agent.dto.run.ChatResponse;
import com.intellermatrix.spending_coach.agent.run.service.RunQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RunController {

    public static final String RUN_ENDPOINT = "/api/runs/{id}";

    private final RunQueryService runQueryService;

    @GetMapping(RUN_ENDPOINT)
    public ChatResponse getRun(@PathVariable UUID id) {
        return runQueryService.getRun(id);
    }
}
