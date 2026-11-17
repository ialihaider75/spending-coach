package com.intellermatrix.spending_coach.agent.util;

import com.intellermatrix.spending_coach.agent.dto.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void sameToolCallArgumentsInDifferentInsertionOrderProduceTheSameKey() {
        Map<String, Object> argumentsA = new LinkedHashMap<>();
        argumentsA.put("b", 2);
        argumentsA.put("a", 1);

        Map<String, Object> argumentsB = new LinkedHashMap<>();
        argumentsB.put("a", 1);
        argumentsB.put("b", 2);

        var a = List.of(new ChatMessage("assistant", null,
            List.of(new ToolCall("call-1", "search", argumentsA)), null));
        var b = List.of(new ChatMessage("assistant", null,
            List.of(new ToolCall("call-1", "search", argumentsB)), null));

        assertThat(ConversationKey.of(a)).isEqualTo(ConversationKey.of(b));
    }
}
