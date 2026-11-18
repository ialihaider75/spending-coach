package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.agent.util.ToolArgumentValidator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AskUserToolTest {

    @Test
    void executeIsNeverSupposedToBeCalled() {
        var tool = new AskUserTool();
        assertThatThrownBy(() -> tool.execute(UUID.randomUUID(), Map.of("question", "ok?")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void definitionRequiresAQuestionArgument() {
        var tool = new AskUserTool();
        assertThat(tool.name()).isEqualTo("ask_user");
        assertThat(ToolArgumentValidator.validate(tool.definition().jsonSchema(), Map.of())).isNotEmpty();
    }
}
