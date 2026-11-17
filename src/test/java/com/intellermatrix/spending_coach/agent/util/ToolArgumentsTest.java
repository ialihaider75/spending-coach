package com.intellermatrix.spending_coach.agent.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolArgumentsTest {

    @Test
    void returnsTheStringValueWhenPresent() {
        assertThat(ToolArguments.optionalString(Map.of("category", "dining"), "category")).isEqualTo("dining");
    }

    @Test
    void returnsNullWhenAbsent() {
        assertThat(ToolArguments.optionalString(Map.of(), "category")).isNull();
    }
}
