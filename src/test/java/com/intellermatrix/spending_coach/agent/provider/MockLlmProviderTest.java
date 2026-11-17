package com.intellermatrix.spending_coach.agent.provider;

import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderRequest;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderResponse;
import com.intellermatrix.spending_coach.agent.util.ConversationKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockLlmProviderTest {

    @Test
    void returnsTheFixtureMatchingTheConversationState() {
        var messages = List.of(new ChatMessage("user", "hi", null, null));
        var expected = new ProviderResponse(new ChatMessage("assistant", "hello", null, null), 10, 5);
        var provider = new MockLlmProvider(Map.of(ConversationKey.of(messages), expected));

        assertThat(provider.chat(new ProviderRequest(messages, List.of()))).isEqualTo(expected);
    }

    @Test
    void throwsWithTheComputedKeyWhenNoFixtureMatches() {
        var provider = new MockLlmProvider(Map.of());
        var messages = List.of(new ChatMessage("user", "hi", null, null));

        assertThatThrownBy(() -> provider.chat(new ProviderRequest(messages, List.of())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(ConversationKey.of(messages));
    }

    @Test
    void nameIsMock() {
        assertThat(new MockLlmProvider(Map.of()).name()).isEqualTo("mock");
    }
}
