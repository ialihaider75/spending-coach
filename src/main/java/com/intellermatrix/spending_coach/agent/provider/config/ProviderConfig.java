package com.intellermatrix.spending_coach.agent.provider.config;

public record ProviderConfig(String name, String baseUrl, String apiKey, String model, int rpm, int tpm, int rpd) {
}
