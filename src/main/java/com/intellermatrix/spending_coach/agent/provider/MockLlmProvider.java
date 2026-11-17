package com.intellermatrix.spending_coach.agent.provider;

import com.intellermatrix.spending_coach.agent.dto.provider.ProviderRequest;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderResponse;
import com.intellermatrix.spending_coach.agent.util.ConversationKey;
import lombok.RequiredArgsConstructor;

import java.util.Map;

// Fixture-miss failures embed the full conversation state in the exception message —
// fine for test/CI use, but never wire this into a real (non-mock) runtime profile.
@RequiredArgsConstructor
public class MockLlmProvider implements LlmProvider {

    private final Map<String, ProviderResponse> fixtures;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public ProviderResponse chat(ProviderRequest request) {
        String key = ConversationKey.of(request.messages());
        ProviderResponse fixture = fixtures.get(key);
        if (fixture == null) {
            throw new IllegalStateException("No mock fixture for conversation state: " + key);
        }
        return fixture;
    }
}
