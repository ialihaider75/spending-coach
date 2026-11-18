package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.dto.tool.ToolExecutionResult;
import com.intellermatrix.spending_coach.agent.tool.AgentTool;
import com.intellermatrix.spending_coach.enums.Status;
import com.intellermatrix.spending_coach.enums.ToolCatalog;
import com.intellermatrix.spending_coach.persistence.entity.BudgetProposal;
import com.intellermatrix.spending_coach.persistence.repository.BudgetProposalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProposeBudgetChangeTool implements AgentTool {

    private static final String SCHEMA = """
        {
        "type":"object",
        "properties":{"changes":{"type":"array","items":{"type":"object","properties":{"category":{"type":"string"},"newMonthlyLimit":{"type":"integer","minimum":0}},"required":["category","newMonthlyLimit"]}}},
        "required":["changes"]
        }
        """;
    private static final int MIN_MONTHLY_LIMIT = 0;
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final BudgetProposalRepository budgetProposalRepository;

    @Override
    public String name() {
        return ToolCatalog.PROPOSE_BUDGET_CHANGE.id();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(name(), ToolCatalog.PROPOSE_BUDGET_CHANGE.definition(), SCHEMA);
    }

    @Override
    public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
        if (!(arguments.get("changes") instanceof List<?> changes)) {
            return errorResult("changes must be a list of {category, newMonthlyLimit} objects");
        }
        if (changes.isEmpty()) {
            return errorResult("changes must contain at least one change");
        }

        for (Object rawChange : changes) {
            if (!(rawChange instanceof Map<?, ?> change)) {
                return errorResult("each change must be an object with a category and a newMonthlyLimit");
            }
            if (!(change.get("category") instanceof String category) || category.isBlank()
                || !(change.get("newMonthlyLimit") instanceof Number newMonthlyLimit)) {
                return errorResult("each change requires a non-blank string category and a numeric newMonthlyLimit");
            }
            if (newMonthlyLimit.longValue() < MIN_MONTHLY_LIMIT) {
                return errorResult("newMonthlyLimit must not be negative");
            }
        }

        String changesJson;
        try {
            changesJson = MAPPER.writeValueAsString(changes);
        } catch (JacksonException e) {
            return errorResult(String.format(Locale.ROOT, "changes could not be serialized: %s", e.getMessage()));
        }

        var proposal = new BudgetProposal(UUID.randomUUID(), userId, changesJson, Status.PENDING, null);
        budgetProposalRepository.save(proposal);

        String summary = String.format(Locale.ROOT, "created a pending proposal for %d change(s)", changes.size());
        return new ToolExecutionResult(name(), Map.of("proposalId", proposal.getId()), summary, false, null);
    }

    private ToolExecutionResult errorResult(String message) {
        return new ToolExecutionResult(name(), null, null, false, message);
    }
}
