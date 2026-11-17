package com.intellermatrix.spending_coach.agent.tool;

import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.dto.tool.ToolExecutionResult;

import java.util.Map;
import java.util.UUID;

public interface AgentTool {

    String name();

    ToolDefinition definition();

    ToolExecutionResult execute(UUID userId, Map<String, Object> arguments);
}
