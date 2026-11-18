package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.dto.tool.ToolExecutionResult;
import com.intellermatrix.spending_coach.agent.tool.AgentTool;
import com.intellermatrix.spending_coach.enums.ToolCatalog;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class AskUserTool implements AgentTool {

    private static final String SCHEMA = """
        {
        "type":"object",
        "properties":{"question":{"type":"string"}},
        "required":["question"]
        }
        """;

    @Override
    public String name() {
        return ToolCatalog.ASK_USER.id();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(name(), ToolCatalog.ASK_USER.definition(), SCHEMA);
    }

    @Override
    public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
        throw new UnsupportedOperationException("ask_user is handled by the agent loop, not the tool registry");
    }
}
