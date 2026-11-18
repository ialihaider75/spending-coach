package com.intellermatrix.spending_coach.agent.loop;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderRequest;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderResponse;
import com.intellermatrix.spending_coach.agent.dto.provider.ToolCall;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void stepCapStopsTheLoopAndReturnsAPartialAnswer() {
        var user = aSavedUser(userRepository);
        var getOverviewCall = new ToolCall("call-1", "get_overview", Map.of());
        var alwaysCallsGetOverview = new ChatMessage("assistant", null, List.of(getOverviewCall), null);
        ChatFunction provider = request -> new ProviderResponse(alwaysCallsGetOverview, 10, 5);
        var loop = new DefaultAgentLoopService(providerFrom(provider), toolRegistry, agentRunRepository,
            agentStepRepository, budgetProposalRepository, Clock.systemUTC());

        var outcome = loop.startRun(user.getId(), "anything");

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(outcome.answer()).containsIgnoringCase("step limit");
        assertThat(agentStepRepository.findByRunId(outcome.runId())).hasSize(8);
    }
}
