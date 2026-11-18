package com.intellermatrix.spending_coach.agent.loop.config;

import com.intellermatrix.spending_coach.agent.loop.service.AgentLoopService;
import com.intellermatrix.spending_coach.agent.loop.service.impl.DefaultAgentLoopService;
import com.intellermatrix.spending_coach.agent.provider.LlmProvider;
import com.intellermatrix.spending_coach.agent.provider.MockLlmProvider;
import com.intellermatrix.spending_coach.agent.tool.ToolRegistry;
import com.intellermatrix.spending_coach.persistence.repository.AgentRunRepository;
import com.intellermatrix.spending_coach.persistence.repository.AgentStepRepository;
import com.intellermatrix.spending_coach.persistence.repository.BudgetProposalRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Map;

@Configuration
public class AgentLoopConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public LlmProvider llmProvider() {
        return new MockLlmProvider(Map.of());
    }

    @Bean
    public AgentLoopService agentLoopService(LlmProvider llmProvider, ToolRegistry toolRegistry,
                                              AgentRunRepository agentRunRepository,
                                              AgentStepRepository agentStepRepository,
                                              BudgetProposalRepository budgetProposalRepository, Clock clock,
                                              AgentLoopProperties properties) {
        return new DefaultAgentLoopService(llmProvider, toolRegistry, agentRunRepository, agentStepRepository,
            budgetProposalRepository, clock, properties);
    }
}
