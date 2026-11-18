package com.intellermatrix.spending_coach.controller;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderResponse;
import com.intellermatrix.spending_coach.agent.dto.provider.ToolCall;
import com.intellermatrix.spending_coach.agent.dto.run.ChatRequest;
import com.intellermatrix.spending_coach.agent.dto.run.ChatResponse;
import com.intellermatrix.spending_coach.agent.loop.SystemPromptBuilder;
import com.intellermatrix.spending_coach.agent.provider.LlmProvider;
import com.intellermatrix.spending_coach.agent.provider.MockLlmProvider;
import com.intellermatrix.spending_coach.agent.util.ConversationKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ChatControllerIT extends IntegrationTestSupport {

    private static final String QUESTION = "I need to save 15000.";
    private static final String PENDING_QUESTION = "Can the gym membership be paused?";

    @Autowired
    private TestRestTemplate rest;

    @TestConfiguration
    static class MockProviderConfig {

        @Bean
        @Primary
        LlmProvider testLlmProvider(Clock clock) {
            var askUserCall = new ToolCall("call-1", "ask_user", Map.of("question", PENDING_QUESTION));
            var pauseMessage = new ChatMessage("assistant", null, List.of(askUserCall), null);
            var doneMessage = new ChatMessage("assistant", "done", null, null);

            List<ChatMessage> firstLeg = List.of(
                new ChatMessage("system", SystemPromptBuilder.build(LocalDate.now(clock)), null, null),
                new ChatMessage("user", QUESTION, null, null));
            List<ChatMessage> secondLeg = new ArrayList<>(firstLeg);
            secondLeg.add(pauseMessage);
            secondLeg.add(new ChatMessage("tool", "yes", null, "call-1"));

            Map<String, ProviderResponse> fixtures = Map.of(
                ConversationKey.of(firstLeg), new ProviderResponse(pauseMessage, 10, 5),
                ConversationKey.of(secondLeg), new ProviderResponse(doneMessage, 10, 5));
            return new MockLlmProvider(fixtures);
        }
    }

    @Test
    void startsAndThenResumesARunThroughTheSameEndpoint() {
        var startResponse = rest.postForEntity("/api/chat",
            new ChatRequest(null, QUESTION, null), ChatResponse.class).getBody();
        assertThat(startResponse.status()).isEqualTo("AWAITING_USER");
        assertThat(startResponse.pendingQuestion()).isEqualTo(PENDING_QUESTION);

        var resumeResponse = rest.postForEntity("/api/chat",
            new ChatRequest(startResponse.runId(), null, "yes"), ChatResponse.class).getBody();
        assertThat(resumeResponse.status()).isEqualTo("COMPLETED");
        assertThat(resumeResponse.answer()).isEqualTo("done");
    }

    @Test
    void resumingAnUnknownRunIdReturns404() {
        var response = rest.postForEntity("/api/chat",
            new ChatRequest(UUID.randomUUID(), null, "yes"), ChatResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void resumingAnAlreadyCompletedRunReturns409() {
        var startResponse = rest.postForEntity("/api/chat",
            new ChatRequest(null, QUESTION, null), ChatResponse.class).getBody();
        rest.postForEntity("/api/chat",
            new ChatRequest(startResponse.runId(), null, "yes"), ChatResponse.class);

        // the run above already paused once and has since completed — resuming it
        // again must be rejected, not silently re-process the stale paused state.
        var response = rest.postForEntity("/api/chat",
            new ChatRequest(startResponse.runId(), null, "yes again"), ChatResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
