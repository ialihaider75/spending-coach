package com.intellermatrix.spending_coach.agent.dto.provider;

import java.util.List;

public record ProviderRequest(List<ChatMessage> messages, List<ToolDefinition> tools) {
}
