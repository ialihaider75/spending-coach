package com.intellermatrix.spending_coach.agent.provider.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderPropertiesTest {

    @Test
    void bindsGeminiAndGroqConfigSeparately() {
        var gemini = new ProviderConfig("gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
            "key-g", "gemini-flash-lite-latest", 15, 250_000, 500);
        var groq = new ProviderConfig("groq", "https://api.groq.com/openai/v1",
            "key-q", "openai/gpt-oss-20b", 30, 8_000, 1_000);
        var properties = new ProviderProperties(gemini, groq);

        assertThat(properties.gemini().model()).isEqualTo("gemini-flash-lite-latest");
        assertThat(properties.groq().tpm()).isEqualTo(8_000);
    }
}
