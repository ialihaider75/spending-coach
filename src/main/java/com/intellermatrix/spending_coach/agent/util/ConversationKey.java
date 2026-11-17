package com.intellermatrix.spending_coach.agent.util;

import com.intellermatrix.spending_coach.agent.dto.ChatMessage;
import lombok.experimental.UtilityClass;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@UtilityClass
public class ConversationKey {

    private final ObjectMapper mapper = JsonMapper.builder()
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .build();

    public String of(List<ChatMessage> messages) {
        return mapper.writeValueAsString(messages);
    }
}
