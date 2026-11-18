package com.intellermatrix.spending_coach.agent.loop.service.impl;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderRequest;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderResponse;
import com.intellermatrix.spending_coach.agent.dto.provider.ToolCall;
import com.intellermatrix.spending_coach.agent.loop.config.AgentLoopProperties;
import com.intellermatrix.spending_coach.agent.provider.LlmProvider;
import com.intellermatrix.spending_coach.agent.tool.ToolRegistry;
import com.intellermatrix.spending_coach.enums.RunStatus;
import com.intellermatrix.spending_coach.persistence.entity.User;
import com.intellermatrix.spending_coach.persistence.repository.AgentRunRepository;
import com.intellermatrix.spending_coach.persistence.repository.AgentStepRepository;
import com.intellermatrix.spending_coach.persistence.repository.BudgetProposalRepository;
import com.intellermatrix.spending_coach.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DefaultAgentLoopServiceIT extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ToolRegistry toolRegistry;
    @Autowired
    private AgentRunRepository agentRunRepository;
    @Autowired
    private AgentStepRepository agentStepRepository;
    @Autowired
    private BudgetProposalRepository budgetProposalRepository;
    @Autowired
    private AgentLoopProperties agentLoopProperties;

    private static User aSavedUser(UserRepository users) {
        return users.save(new User(UUID.randomUUID(), "Test User", Instant.now()));
    }

    private static LlmProvider providerFrom(ChatFunction function) {
        return new LlmProvider() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public ProviderResponse chat(ProviderRequest request) {
                return function.chat(request);
            }
        };
    }

    @FunctionalInterface
    private interface ChatFunction {
        ProviderResponse chat(ProviderRequest request);
    }

    private static Clock advancingClock(Duration increment) {
        return new Clock() {
            private Instant current = Instant.EPOCH;

            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                current = current.plus(increment);
                return current;
            }
        };
    }

    @Test
    void stepCapStopsTheLoopAndReturnsAPartialAnswer() {
        var user = aSavedUser(userRepository);
        var callCounter = new AtomicInteger();
        // each call uses a different argument so loop detection (steps 6-7) never fires
        // here — this test is specifically about the step cap, not repeat detection.
        ChatFunction provider = request -> {
            var toolCall = new ToolCall("call-" + callCounter.get(), "get_overview",
                Map.of("call", callCounter.incrementAndGet()));
            var message = new ChatMessage("assistant", null, List.of(toolCall), null);
            return new ProviderResponse(message, 10, 5);
        };
        var loop = new DefaultAgentLoopService(providerFrom(provider), toolRegistry, agentRunRepository,
            agentStepRepository, budgetProposalRepository, Clock.systemUTC(), agentLoopProperties);

        var outcome = loop.startRun(user.getId(), "anything");

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(outcome.answer()).containsIgnoringCase("step limit");
        assertThat(agentStepRepository.findByRunId(outcome.runId())).hasSize(8);
    }

    @Test
    void thirdIdenticalToolCallAbortsTheRun() {
        var user = aSavedUser(userRepository);
        var repeatedCall = new ToolCall("call-1", "get_overview", Map.of());
        var alwaysRepeatsTheSameCall = new ChatMessage("assistant", null, List.of(repeatedCall), null);
        ChatFunction provider = request -> new ProviderResponse(alwaysRepeatsTheSameCall, 10, 5);
        var loop = new DefaultAgentLoopService(providerFrom(provider), toolRegistry, agentRunRepository,
            agentStepRepository, budgetProposalRepository, Clock.systemUTC(), agentLoopProperties);

        var outcome = loop.startRun(user.getId(), "anything");

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(agentStepRepository.findByRunId(outcome.runId())).hasSize(3);
    }

    @Test
    void wallClockCapStopsTheLoopEvenBeforeTheStepCap() {
        var user = aSavedUser(userRepository);
        var getOverviewCall = new ToolCall("call-1", "get_overview", Map.of());
        var alwaysCallsGetOverview = new ChatMessage("assistant", null, List.of(getOverviewCall), null);
        ChatFunction provider = request -> new ProviderResponse(alwaysCallsGetOverview, 10, 5);
        var loop = new DefaultAgentLoopService(providerFrom(provider), toolRegistry, agentRunRepository,
            agentStepRepository, budgetProposalRepository, advancingClock(Duration.ofSeconds(20)), agentLoopProperties);

        var outcome = loop.startRun(user.getId(), "anything");

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(outcome.answer()).containsIgnoringCase("time limit");
        assertThat(agentStepRepository.findByRunId(outcome.runId())).hasSizeLessThan(8);
    }

    @Test
    void askUserPausesAndResumeContinuesTheSameConversation() {
        var user = aSavedUser(userRepository);
        var askUserCall = new ToolCall("call-1", "ask_user", Map.of("question", "Can the gym membership be paused?"));
        ChatFunction pausesOnFirstCall = request -> request.messages().size() <= 2
            ? new ProviderResponse(new ChatMessage("assistant", null, List.of(askUserCall), null), 10, 5)
            : new ProviderResponse(new ChatMessage("assistant", "done", null, null), 10, 5);
        var loop = new DefaultAgentLoopService(providerFrom(pausesOnFirstCall), toolRegistry, agentRunRepository,
            agentStepRepository, budgetProposalRepository, Clock.systemUTC(), agentLoopProperties);

        var outcome = loop.startRun(user.getId(), "I need to save 15000.");
        assertThat(outcome.status()).isEqualTo(RunStatus.AWAITING_USER);
        assertThat(outcome.pendingQuestion()).isEqualTo("Can the gym membership be paused?");

        var resumed = loop.resumeRun(outcome.runId(), "yes");
        assertThat(resumed.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(resumed.answer()).isEqualTo("done");
    }

    @Test
    void resumingARunThatAlreadyFinishedAfterAnEarlierResumeFails() {
        var user = aSavedUser(userRepository);
        var askUserCall = new ToolCall("call-1", "ask_user", Map.of("question", "Can the gym membership be paused?"));
        ChatFunction pausesOnFirstCall = request -> request.messages().size() <= 2
            ? new ProviderResponse(new ChatMessage("assistant", null, List.of(askUserCall), null), 10, 5)
            : new ProviderResponse(new ChatMessage("assistant", "done", null, null), 10, 5);
        var loop = new DefaultAgentLoopService(providerFrom(pausesOnFirstCall), toolRegistry, agentRunRepository,
            agentStepRepository, budgetProposalRepository, Clock.systemUTC(), agentLoopProperties);
        var outcome = loop.startRun(user.getId(), "I need to save 15000.");
        loop.resumeRun(outcome.runId(), "yes");

        // conversationState must be cleared once consumed — otherwise a second resume
        // would silently re-process the stale pre-resume snapshot instead of failing.
        assertThatThrownBy(() -> loop.resumeRun(outcome.runId(), "yes again"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resumingAnUnknownRunIdFails() {
        ChatFunction neverCalled = request -> {
            throw new AssertionError("provider should never be called for an unknown run id");
        };
        var loop = new DefaultAgentLoopService(providerFrom(neverCalled), toolRegistry, agentRunRepository,
            agentStepRepository, budgetProposalRepository, Clock.systemUTC(), agentLoopProperties);

        assertThatThrownBy(() -> loop.resumeRun(UUID.randomUUID(), "yes"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void persistsOneAgentStepPerToolCallWithAllAuditFields() {
        var user = aSavedUser(userRepository);
        var getOverviewCall = new ToolCall("call-1", "get_overview", Map.of());
        ChatFunction callThenAnswer = request -> request.messages().size() <= 2
            ? new ProviderResponse(new ChatMessage("assistant", null, List.of(getOverviewCall), null), 10, 5)
            : new ProviderResponse(new ChatMessage("assistant", "done", null, null), 7, 3);
        var loop = new DefaultAgentLoopService(providerFrom(callThenAnswer), toolRegistry, agentRunRepository,
            agentStepRepository, budgetProposalRepository, Clock.systemUTC(), agentLoopProperties);

        var outcome = loop.startRun(user.getId(), "anything");

        var steps = agentStepRepository.findByRunId(outcome.runId());
        assertThat(steps).hasSize(1);
        var step = steps.get(0);
        assertThat(step.getStepNumber()).isEqualTo(1);
        assertThat(step.getTool()).isEqualTo("get_overview");
        assertThat(step.getResultSummary()).isNotNull();
        assertThat(step.getLatencyMs()).isNotNull();
        assertThat(step.getError()).isNull();

        var savedRun = agentRunRepository.findById(outcome.runId()).orElseThrow();
        assertThat(savedRun.getTotalTokens()).isEqualTo(10 + 5 + 7 + 3);
        assertThat(savedRun.getTotalLatencyMs()).isNotNull();
    }

    @Test
    void askUserCallWithNullArgumentsPausesWithoutThrowing() {
        var user = aSavedUser(userRepository);
        var askUserCall = new ToolCall("call-1", "ask_user", null);
        ChatFunction provider = request -> new ProviderResponse(
            new ChatMessage("assistant", null, List.of(askUserCall), null), 10, 5);
        var loop = new DefaultAgentLoopService(providerFrom(provider), toolRegistry, agentRunRepository,
            agentStepRepository, budgetProposalRepository, Clock.systemUTC(), agentLoopProperties);

        var outcome = loop.startRun(user.getId(), "anything");

        assertThat(outcome.status()).isEqualTo(RunStatus.AWAITING_USER);
        assertThat(outcome.pendingQuestion()).isNull();
    }

    @Test
    void askUserAlongsideAnotherToolCallInTheSameBatchStillResumesCorrectly() {
        var user = aSavedUser(userRepository);
        var getOverviewCall = new ToolCall("call-1", "get_overview", Map.of());
        var askUserCall = new ToolCall("call-2", "ask_user", Map.of("question", "Can the gym membership be paused?"));
        ChatFunction pausesOnFirstCall = request -> request.messages().size() <= 2
            ? new ProviderResponse(new ChatMessage("assistant", null, List.of(getOverviewCall, askUserCall), null), 10, 5)
            : new ProviderResponse(new ChatMessage("assistant", "done", null, null), 10, 5);
        var loop = new DefaultAgentLoopService(providerFrom(pausesOnFirstCall), toolRegistry, agentRunRepository,
            agentStepRepository, budgetProposalRepository, Clock.systemUTC(), agentLoopProperties);

        var outcome = loop.startRun(user.getId(), "anything");
        assertThat(outcome.status()).isEqualTo(RunStatus.AWAITING_USER);

        var resumed = loop.resumeRun(outcome.runId(), "yes");

        assertThat(resumed.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(resumed.answer()).isEqualTo("done");
    }

    @Test
    void unserializableToolCallArgumentsDoNotCrashTheRun() {
        var user = aSavedUser(userRepository);
        // deeply nested (not cyclic) so it passes schema validation's Jackson 2
        // valueToTree untouched, but trips Jackson 3's stricter nesting-depth limit in
        // dispatch()/canonicalKey() — exercising those catch branches specifically.
        Map<String, Object> deepest = new HashMap<>();
        Map<String, Object> current = deepest;
        for (int i = 0; i < 600; i++) {
            Map<String, Object> next = new HashMap<>();
            current.put("n", next);
            current = next;
        }
        var badCall = new ToolCall("call-1", "get_overview", deepest);
        ChatFunction provider = request -> request.messages().size() <= 2
            ? new ProviderResponse(new ChatMessage("assistant", null, List.of(badCall), null), 10, 5)
            : new ProviderResponse(new ChatMessage("assistant", "done", null, null), 7, 3);
        var loop = new DefaultAgentLoopService(providerFrom(provider), toolRegistry, agentRunRepository,
            agentStepRepository, budgetProposalRepository, Clock.systemUTC(), agentLoopProperties);

        var outcome = loop.startRun(user.getId(), "anything");

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(outcome.answer()).isEqualTo("done");
        assertThat(agentStepRepository.findByRunId(outcome.runId())).hasSize(1);
    }

    @Test
    void resumingARunThatIsNotPausedFails() {
        var user = aSavedUser(userRepository);
        ChatFunction answersImmediately = request -> new ProviderResponse(
            new ChatMessage("assistant", "done", null, null), 10, 5);
        var loop = new DefaultAgentLoopService(providerFrom(answersImmediately), toolRegistry, agentRunRepository,
            agentStepRepository, budgetProposalRepository, Clock.systemUTC(), agentLoopProperties);

        var outcome = loop.startRun(user.getId(), "anything");
        assertThat(outcome.status()).isEqualTo(RunStatus.COMPLETED);

        assertThatThrownBy(() -> loop.resumeRun(outcome.runId(), "yes"))
            .isInstanceOf(IllegalStateException.class);
    }
}
