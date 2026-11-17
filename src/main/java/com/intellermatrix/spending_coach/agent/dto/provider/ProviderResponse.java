package com.intellermatrix.spending_coach.agent.dto.provider;

public record ProviderResponse(ChatMessage message, int promptTokens, int completionTokens) {
}
