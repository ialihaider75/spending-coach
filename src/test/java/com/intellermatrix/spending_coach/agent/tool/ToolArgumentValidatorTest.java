package com.intellermatrix.spending_coach.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolArgumentValidatorTest {

    private static final String SCHEMA = """
        {"type":"object","properties":{"limit":{"type":"integer","maximum":50}},"required":["limit"]}""";

    @Test
    void validArgumentsProduceNoErrors() {
        assertThat(ToolArgumentValidator.validate(SCHEMA, Map.of("limit", 10))).isEmpty();
    }

    @Test
    void missingRequiredArgumentProducesAnError() {
        assertThat(ToolArgumentValidator.validate(SCHEMA, Map.of())).isNotEmpty();
    }

    @Test
    void argumentExceedingItsSchemaBoundProducesAnError() {
        assertThat(ToolArgumentValidator.validate(SCHEMA, Map.of("limit", 999))).isNotEmpty();
    }
}
