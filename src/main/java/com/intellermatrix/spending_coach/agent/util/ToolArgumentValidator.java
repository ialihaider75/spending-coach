package com.intellermatrix.spending_coach.agent.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@UtilityClass
public class ToolArgumentValidator {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final String cyclicArgumentsError = "arguments must not contain a self-referencing/cyclic structure";

    public List<String> validate(String jsonSchema, Map<String, Object> arguments) {
        // Jackson 2's valueToTree(...) below has no cycle detection and recurses
        // without bound on a self-referencing map/list, throwing an uncatchable
        // StackOverflowError - reject the cycle ourselves first, as a structured error.
        if (containsCycle(arguments, Collections.newSetFromMap(new IdentityHashMap<>()))) {
            return List.of(cyclicArgumentsError);
        }

        JsonSchema schema = factory.getSchema(jsonSchema);
        JsonNode node = mapper.valueToTree(arguments);
        return schema.validate(node).stream().map(ValidationMessage::getMessage).toList();
    }

    private boolean containsCycle(Object value, Set<Object> visiting) {
        if (value instanceof Map<?, ?> map) {
            return containsCycleInChildren(map, map.values(), visiting);
        }
        if (value instanceof List<?> list) {
            return containsCycleInChildren(list, list, visiting);
        }
        return false;
    }

    private boolean containsCycleInChildren(Object container, Iterable<?> children, Set<Object> visiting) {
        if (!visiting.add(container)) {
            return true;
        }
        for (Object child : children) {
            if (containsCycle(child, visiting)) {
                return true;
            }
        }
        visiting.remove(container);
        return false;
    }
}
