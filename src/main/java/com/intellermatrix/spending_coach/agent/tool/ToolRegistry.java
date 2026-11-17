package com.intellermatrix.spending_coach.agent.tool;

import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.dto.tool.ToolExecutionResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ToolRegistry {

    List<ToolDefinition> definitions();

    ToolExecutionResult execute(UUID userId, String toolName, Map<String, Object> arguments);
}
