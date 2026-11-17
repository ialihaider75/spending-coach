package com.intellermatrix.spending_coach.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;

@UtilityClass
public class ToolArgumentValidator {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public List<String> validate(String jsonSchema, Map<String, Object> arguments) {
        JsonSchema schema = factory.getSchema(jsonSchema);
        JsonNode node = mapper.valueToTree(arguments);
        return schema.validate(node).stream().map(ValidationMessage::getMessage).toList();
    }
}
