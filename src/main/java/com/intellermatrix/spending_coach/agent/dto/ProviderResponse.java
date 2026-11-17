package com.intellermatrix.spending_coach.agent.dto;

public record ProviderResponse(ChatMessage message, int promptTokens, int completionTokens) {
}
