package com.intellermatrix.spending_coach.agent.dto;

import java.util.List;

public record ProviderRequest(List<ChatMessage> messages, List<ToolDefinition> tools) {
}
