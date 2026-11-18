package com.intellermatrix.spending_coach.controller;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.run.ChatResponse;
import com.intellermatrix.spending_coach.enums.RunStatus;
import com.intellermatrix.spending_coach.persistence.entity.AgentRun;
import com.intellermatrix.spending_coach.persistence.entity.AgentStep;
import com.intellermatrix.spending_coach.persistence.entity.User;
import com.intellermatrix.spending_coach.persistence.repository.AgentRunRepository;
import com.intellermatrix.spending_coach.persistence.repository.AgentStepRepository;
import com.intellermatrix.spending_coach.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RunControllerIT extends IntegrationTestSupport {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AgentRunRepository agentRunRepository;
    @Autowired
    private AgentStepRepository agentStepRepository;

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    @Test
    void getRunReturnsTheRunAndItsTrace() {
        var user = aSavedUser(userRepository);
        var run = new AgentRun(UUID.randomUUID(), user.getId(), "How much did I spend on dining?", "mock",
            RunStatus.COMPLETED, 25, 100, Instant.now(), null);
        agentRunRepository.save(run);
        agentStepRepository.save(new AgentStep(run.getId(), 1, "get_overview", "{}", "summary", 12, null, null));

        var response = rest.getForEntity("/api/runs/" + run.getId(), ChatResponse.class).getBody();

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.trace()).hasSize(1);
        assertThat(response.trace().get(0).tool()).isEqualTo("get_overview");
        assertThat(response.trace().get(0).stepNumber()).isEqualTo(1);
    }

    @Test
    void gettingAnUnknownRunReturns404() {
        var response = rest.getForEntity("/api/runs/" + UUID.randomUUID(), ChatResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
