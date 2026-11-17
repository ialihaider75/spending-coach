package com.intellermatrix.spending_coach.agent.dto.provider;

import java.util.Map;

public record ToolCall(String id, String toolName, Map<String, Object> arguments) {
}
