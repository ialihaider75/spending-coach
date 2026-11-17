package com.intellermatrix.spending_coach.agent.tool;

import com.intellermatrix.spending_coach.agent.dto.ToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class DefaultToolRegistry implements ToolRegistry {

    private final List<AgentTool> tools;

    public DefaultToolRegistry(List<AgentTool> tools) {
        var duplicates = tools.stream()
            .collect(Collectors.groupingBy(AgentTool::name, Collectors.counting()))
            .entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate tool name(s) registered: " + duplicates);
        }
        this.tools = tools;
    }

    @Override
    public List<ToolDefinition> definitions() {
        return tools.stream().map(AgentTool::definition).toList();
    }

    @Override
    public ToolExecutionResult execute(UUID userId, String toolName, Map<String, Object> arguments) {
        try {
            AgentTool tool = tools.stream()
                .filter(candidate -> candidate.name().equals(toolName))
                .findFirst()
                .orElse(null);
            if (tool == null) {
                return new ToolExecutionResult(toolName, null, null, false, "Unknown tool: " + toolName);
            }

            List<String> errors = ToolArgumentValidator.validate(tool.definition().jsonSchema(), arguments);
            if (!errors.isEmpty()) {
                return new ToolExecutionResult(toolName, null, null, false, String.join("; ", errors));
            }

            return tool.execute(userId, arguments);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            return new ToolExecutionResult(toolName, null, null, false, message);
        }
    }
}
