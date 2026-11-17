package com.intellermatrix.spending_coach.agent.tool;

import com.intellermatrix.spending_coach.agent.dto.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultToolRegistryTest {

    private static final String SPY_SCHEMA = """
        {"type":"object","properties":{"n":{"type":"integer"}},"required":["n"]}""";

    private static final class SpyTool implements AgentTool {
        private boolean executed = false;

        @Override
        public String name() {
            return "spy_tool";
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("spy_tool", "test", SPY_SCHEMA);
        }

        @Override
        public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
            executed = true;
            return new ToolExecutionResult("spy_tool", "ok", "ok", false, null);
        }
    }

    @Test
    void unknownToolNameReturnsAStructuredError() {
        var registry = new DefaultToolRegistry(List.of());

        var result = registry.execute(UUID.randomUUID(), "not_a_real_tool", Map.of());

        assertThat(result.error()).isNotNull();
        assertThat(result.payload()).isNull();
    }

    @Test
    void invalidArgumentsAreRejectedBeforeTheToolRunsAtAll() {
        var spy = new SpyTool();
        var registry = new DefaultToolRegistry(List.of(spy));

        var result = registry.execute(UUID.randomUUID(), "spy_tool", Map.of());

        assertThat(result.error()).isNotNull();
        assertThat(spy.executed).isFalse();
    }

    @Test
    void validCallDispatchesToTheMatchingTool() {
        var spy = new SpyTool();
        var registry = new DefaultToolRegistry(List.of(spy));

        var result = registry.execute(UUID.randomUUID(), "spy_tool", Map.of("n", 1));

        assertThat(spy.executed).isTrue();
        assertThat(result.payload()).isEqualTo("ok");
        assertThat(result.error()).isNull();
    }

    @Test
    void definitionsReturnsEveryRegisteredToolsDefinition() {
        var registry = new DefaultToolRegistry(List.of(new SpyTool()));

        assertThat(registry.definitions()).extracting(ToolDefinition::name).containsExactly("spy_tool");
    }
}
