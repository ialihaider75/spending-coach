package com.intellermatrix.spending_coach.enums;

import java.util.Arrays;
import java.util.Optional;

public enum Kind {
    EXPENSE,
    INCOME;

    public static Optional<Kind> fromString(String value) {
        return Arrays.stream(values())
            .filter(kind -> kind.name().equalsIgnoreCase(value))
            .findFirst();
    }
}
