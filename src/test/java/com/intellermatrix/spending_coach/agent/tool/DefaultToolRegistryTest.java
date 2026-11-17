package com.intellermatrix.spending_coach.agent.tool;

import com.intellermatrix.spending_coach.agent.dto.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void aMalformedSchemaOnTheToolsOwnDefinitionReturnsAStructuredErrorInsteadOfThrowing() {
        var brokenSchemaTool = new AgentTool() {
            @Override
            public String name() {
                return "broken_schema_tool";
            }

            @Override
            public ToolDefinition definition() {
                return new ToolDefinition("broken_schema_tool", "test", "{not valid json");
            }

            @Override
            public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
                return new ToolExecutionResult("broken_schema_tool", "unreachable", "unreachable", false, null);
            }
        };
        var registry = new DefaultToolRegistry(List.of(brokenSchemaTool));

        var result = registry.execute(UUID.randomUUID(), "broken_schema_tool", Map.of());

        assertThat(result.error()).isNotNull();
    }

    @Test
    void anExceptionWithNoMessageStillProducesANonNullError() {
        var throwsWithNoMessage = new AgentTool() {
            @Override
            public String name() {
                return "throwing_tool";
            }

            @Override
            public ToolDefinition definition() {
                return new ToolDefinition("throwing_tool", "test", """
                    {"type":"object","properties":{}}""");
            }

            @Override
            public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
                throw new IllegalStateException();
            }
        };
        var registry = new DefaultToolRegistry(List.of(throwsWithNoMessage));

        var result = registry.execute(UUID.randomUUID(), "throwing_tool", Map.of());

        assertThat(result.error()).isNotNull();
    }

    @Test
    void duplicateToolNamesAreRejectedAtConstructionTime() {
        assertThatThrownBy(() -> new DefaultToolRegistry(List.of(new SpyTool(), new SpyTool())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("spy_tool");
    }
}
