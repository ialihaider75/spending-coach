package com.intellermatrix.spending_coach.agent;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderRequest;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderResponse;
import com.intellermatrix.spending_coach.agent.dto.provider.ToolCall;
import com.intellermatrix.spending_coach.agent.loop.config.AgentLoopProperties;
import com.intellermatrix.spending_coach.agent.loop.service.AgentLoopService;
import com.intellermatrix.spending_coach.agent.loop.service.impl.DefaultAgentLoopService;
import com.intellermatrix.spending_coach.agent.provider.LlmProvider;
import com.intellermatrix.spending_coach.agent.tool.ToolRegistry;
import com.intellermatrix.spending_coach.enums.RunStatus;
import com.intellermatrix.spending_coach.generator.SyntheticDataGenerator;
import com.intellermatrix.spending_coach.ingestion.service.TransactionImportService;
import com.intellermatrix.spending_coach.persistence.entity.AgentStep;
import com.intellermatrix.spending_coach.persistence.entity.User;
import com.intellermatrix.spending_coach.persistence.repository.AgentRunRepository;
import com.intellermatrix.spending_coach.persistence.repository.AgentStepRepository;
import com.intellermatrix.spending_coach.persistence.repository.BudgetProposalRepository;
import com.intellermatrix.spending_coach.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// This milestone's acceptance gate (spec §12): every idea-doc "Demo scripts" question
// must complete end to end against the real dev dataset. The provider is still mocked
// (Milestone 4 adds a real one), so each test scripts the exact reply sequence a real
// model would plausibly make and asserts the loop/tool/pause-resume machinery around it
// actually works - not that a model would choose these exact tool calls.
@SpringBootTest
class DemoScriptsIT extends IntegrationTestSupport {

    private static final long DEV_SEED = 42L;
    private static final LocalDate DEV_ANCHOR_DATE = LocalDate.of(2026, 10, 31);

    @Autowired
    private SyntheticDataGenerator generator;
    @Autowired
    private TransactionImportService importService;
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
    @Autowired
    private Clock clock;

    private UUID devUserId;

    @BeforeEach
    void loadDevDataset() {
        User user = userRepository.save(new User(UUID.randomUUID(), "Dev User", Instant.now()));
        devUserId = user.getId();
        String csv = generator.generateCsv(DEV_SEED, DEV_ANCHOR_DATE);
        importService.importCsv(devUserId, new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    private AgentLoopService loopWith(LlmProvider provider) {
        return new DefaultAgentLoopService(provider, toolRegistry, agentRunRepository, agentStepRepository,
            budgetProposalRepository, clock, agentLoopProperties);
    }

    private static ChatMessage toolCall(String toolName, Map<String, Object> arguments) {
        var call = new ToolCall("call-" + UUID.randomUUID(), toolName, arguments);
        return new ChatMessage("assistant", null, List.of(call), null);
    }

    private static ChatMessage finalAnswer(String text) {
        return new ChatMessage("assistant", text, null, null);
    }

    // Deliberately not a hash-keyed MockLlmProvider: the scripted replies here follow
    // real tool results computed against live seeded data, so the exact conversation
    // content can't be known upfront. This mirrors DefaultAgentLoopServiceIT's own
    // lambda-based LlmProvider, just driven by call order instead of a condition.
    private static LlmProvider scriptedProvider(ChatMessage... replies) {
        Queue<ChatMessage> queue = new ArrayDeque<>(List.of(replies));
        return new LlmProvider() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public ProviderResponse chat(ProviderRequest request) {
                ChatMessage next = queue.poll();
                if (next == null) {
                    throw new AssertionError("scripted provider ran out of replies");
                }
                return new ProviderResponse(next, 10, 5);
            }
        };
    }

    @Test
    void demoScript1_diningSpikeExplainsTheOctoberShortfall() {
        var provider = scriptedProvider(
            toolCall("get_overview", Map.of()),
            toolCall("project_month_end", Map.of()),
            toolCall("compare_periods", Map.of(
                "periodAFrom", "2026-10-01", "periodATo", "2026-10-31",
                "periodBFrom", "2026-07-01", "periodBTo", "2026-09-30")),
            toolCall("top_transactions", Map.of("category", "dining", "from", "2026-10-01", "to", "2026-10-31")),
            toolCall("search_transactions", Map.of("category", "dining", "text", "delivery")),
            finalAnswer("The shortfall comes from frequent weekend delivery orders in October."));

        var outcome = loopWith(provider)
            .startRun(devUserId, "Why am I running out of money before the 25th this month?");

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(outcome.answer()).containsIgnoringCase("delivery");
    }

    @Test
    void demoScript2_savingsPlanPausesForUserThenProposesABudgetChange() {
        var provider = scriptedProvider(
            toolCall("get_overview", Map.of()),
            toolCall("find_recurring_charges", Map.of()),
            toolCall("simulate_savings", Map.of("adjustments", List.of(
                Map.of("category", "dining", "cutPercent", 40),
                Map.of("category", "subscription", "cutPercent", 100)))),
            toolCall("simulate_savings", Map.of("adjustments", List.of(
                Map.of("category", "dining", "cutPercent", 40),
                Map.of("category", "subscription", "cutPercent", 100),
                Map.of("category", "groceries", "cutPercent", 10)))),
            toolCall("ask_user", Map.of("question", "Can dining be cut further to close the remaining gap?")),
            toolCall("simulate_savings", Map.of("adjustments", List.of(
                Map.of("category", "dining", "cutPercent", 100),
                Map.of("category", "subscription", "cutPercent", 100),
                Map.of("category", "groceries", "cutPercent", 3)))),
            toolCall("propose_budget_change", Map.of("changes", List.of(
                Map.of("category", "dining", "newMonthlyLimit", 0),
                Map.of("category", "subscription", "newMonthlyLimit", 0),
                Map.of("category", "groceries", "newMonthlyLimit", 35931)))),
            finalAnswer("A dining and subscription freeze plus a small grocery cut clears the 15,000 target; "
                + "the plan is now pending your approval."));

        var loop = loopWith(provider);
        var outcome = loop.startRun(devUserId,
            "I need to save 15,000 by month end. Rent and school fees can't change.");

        assertThat(outcome.status()).isEqualTo(RunStatus.AWAITING_USER);
        assertThat(outcome.pendingQuestion()).isEqualTo("Can dining be cut further to close the remaining gap?");

        var resumed = loop.resumeRun(outcome.runId(), "yes");

        assertThat(resumed.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(budgetProposalRepository.findAll().stream()
            .filter(proposal -> proposal.getUserId().equals(devUserId)))
            .hasSize(1);

        // Locks in the Task 12 fix: steps dispatched after resuming must not overwrite
        // the ones dispatched before the pause (closes the gap an independent review
        // pointed out - this test previously asserted nothing about agent_steps).
        List<AgentStep> steps = agentStepRepository.findByRunId(resumed.runId()).stream()
            .sorted(Comparator.comparingInt(AgentStep::getStepNumber))
            .toList();
        assertThat(steps).hasSize(6);
        assertThat(steps.stream().map(AgentStep::getTool)).containsExactly(
            "get_overview", "find_recurring_charges", "simulate_savings", "simulate_savings",
            "simulate_savings", "propose_budget_change");
    }

    @Test
    void demoScript3_reportsRealAnomaliesAndDropsTheNormalOnes() {
        var provider = scriptedProvider(
            toolCall("detect_anomalies", Map.of()),
            toolCall("search_transactions", Map.of("category", "shopping", "text", "purchase")),
            toolCall("search_transactions", Map.of("category", "subscription")),
            finalAnswer("Two things stand out: a duplicate 4,367 shopping charge on 2026-05-12, and a "
                + "subscription that rose from 1,024 to 1,364. Everything else looks like normal "
                + "month-to-month spending."));

        var outcome = loopWith(provider).startRun(devUserId, "Anything weird in my spending?");

        assertThat(outcome.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(outcome.answer()).containsIgnoringCase("duplicate");
        assertThat(outcome.answer()).containsIgnoringCase("subscription");
        assertThat(outcome.answer()).doesNotContainIgnoringCase("rent");
        assertThat(outcome.answer()).doesNotContainIgnoringCase("salary");
    }
}
