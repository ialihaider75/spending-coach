package com.intellermatrix.spending_coach.agent.dto.provider;

import java.util.List;

public record ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
}
