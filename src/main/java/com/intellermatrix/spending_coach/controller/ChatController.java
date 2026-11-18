package com.intellermatrix.spending_coach.controller;

import com.intellermatrix.spending_coach.agent.dto.run.ChatRequest;
import com.intellermatrix.spending_coach.agent.dto.run.ChatResponse;
import com.intellermatrix.spending_coach.agent.loop.service.AgentLoopService;
import com.intellermatrix.spending_coach.agent.run.service.RunQueryService;
import com.intellermatrix.spending_coach.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatController {

    public static final String CHAT_ENDPOINT = "/api/chat";

    private final AgentLoopService agentLoopService;
    private final RunQueryService runQueryService;
    private final CurrentUserService currentUserService;

    @PostMapping(CHAT_ENDPOINT)
    public ChatResponse chat(@RequestBody ChatRequest request) {
        var outcome = request.runId() == null
            ? agentLoopService.startRun(currentUserService.resolveUserId(), request.question())
            : agentLoopService.resumeRun(request.runId(), request.answer());
        return runQueryService.toChatResponse(outcome);
    }
}
