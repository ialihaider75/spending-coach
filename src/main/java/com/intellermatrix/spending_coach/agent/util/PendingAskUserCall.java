package com.intellermatrix.spending_coach.agent.util;

import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.provider.ToolCall;
import com.intellermatrix.spending_coach.enums.ToolCatalog;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Optional;

@UtilityClass
public class PendingAskUserCall {

    public Optional<ToolCall> findIn(List<ChatMessage> messages) {
        return messages.stream()
            .filter(message -> message.toolCalls() != null)
            .flatMap(message -> message.toolCalls().stream())
            .filter(toolCall -> ToolCatalog.ASK_USER.id().equals(toolCall.toolName()))
            .reduce((first, mostRecent) -> mostRecent);
    }
}
