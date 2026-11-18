package com.intellermatrix.spending_coach.agent.loop;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptBuilderTest {

    @Test
    void mentionsTodayGroundingAndUntrustedTextRules() {
        String prompt = SystemPromptBuilder.build(LocalDate.of(2026, 10, 31));

        assertThat(prompt).contains("2026-10-31");
        assertThat(prompt.toLowerCase()).contains("tool result");
        assertThat(prompt.toLowerCase()).contains("ignore");
    }
}
