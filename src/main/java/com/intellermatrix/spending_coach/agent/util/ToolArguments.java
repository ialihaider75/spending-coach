package com.intellermatrix.spending_coach.agent.util;

import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public class ToolArguments {

    public String optionalString(Map<String, Object> arguments, String key) {
        return (String) arguments.get(key);
    }
}
