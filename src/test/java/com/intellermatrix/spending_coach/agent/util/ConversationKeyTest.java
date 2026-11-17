package com.intellermatrix.spending_coach.agent.util;

import com.intellermatrix.spending_coach.agent.dto.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationKeyTest {

    @Test
    void identicalConversationsProduceTheSameKey() {
        var a = List.of(new ChatMessage("user", "hi", null, null));
        var b = List.of(new ChatMessage("user", "hi", null, null));

        assertThat(ConversationKey.of(a)).isEqualTo(ConversationKey.of(b));
    }

    @Test
    void differentContentProducesADifferentKey() {
        var a = List.of(new ChatMessage("user", "hi", null, null));
        var b = List.of(new ChatMessage("user", "bye", null, null));

        assertThat(ConversationKey.of(a)).isNotEqualTo(ConversationKey.of(b));
    }
}
