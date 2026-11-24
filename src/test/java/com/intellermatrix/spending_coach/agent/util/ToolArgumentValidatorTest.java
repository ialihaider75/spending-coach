package com.intellermatrix.spending_coach.agent.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolArgumentValidatorTest {

    private static final String SCHEMA = """
        {"type":"object","properties":{"limit":{"type":"integer","maximum":50}},"required":["limit"]}""";
    private static final String OPEN_SCHEMA = """
        {"type":"object","properties":{}}""";

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

    @Test
    void selfReferencingArgumentsProduceAStructuredErrorInsteadOfCrashing() {
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);

        assertThat(ToolArgumentValidator.validate(OPEN_SCHEMA, cyclic))
            .anyMatch(message -> message.toLowerCase(Locale.ROOT).contains("cyclic")
                || message.toLowerCase(Locale.ROOT).contains("self-referencing"));
    }

    @Test
    void deeplyNestedButNonCyclicArgumentsStillValidateNormally() {
        Map<String, Object> deepest = new HashMap<>();
        Map<String, Object> current = deepest;
        for (int i = 0; i < 600; i++) {
            Map<String, Object> next = new HashMap<>();
            current.put("n", next);
            current = next;
        }

        assertThat(ToolArgumentValidator.validate(OPEN_SCHEMA, Map.of("nested", deepest))).isEmpty();
    }
}
