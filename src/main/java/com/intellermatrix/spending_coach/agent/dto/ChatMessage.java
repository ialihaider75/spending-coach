package com.intellermatrix.spending_coach.agent.dto;

import java.util.List;

public record ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
}
