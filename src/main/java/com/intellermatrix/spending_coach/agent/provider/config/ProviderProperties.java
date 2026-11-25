package com.intellermatrix.spending_coach.agent.provider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.providers")
public record ProviderProperties(ProviderConfig gemini, ProviderConfig groq) {
}
