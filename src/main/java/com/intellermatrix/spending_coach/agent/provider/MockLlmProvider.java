package com.intellermatrix.spending_coach.agent.provider;

import com.intellermatrix.spending_coach.agent.dto.ProviderRequest;
import com.intellermatrix.spending_coach.agent.dto.ProviderResponse;
import com.intellermatrix.spending_coach.agent.util.ConversationKey;
import lombok.RequiredArgsConstructor;

import java.util.Map;

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
