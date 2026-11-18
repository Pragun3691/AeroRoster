package com.aeroroster.service.validation;

import java.util.Locale;

public final class InputValues {
    private InputValues() {
    }

    public static String text(String value) {
        return value == null ? null : value.strip();
    }

    public static String identifier(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    /** Escape user text so SQL LIKE treats percent and underscore literally. */
    public static String searchPattern(String value) {
        if (value == null) {
            return "%";
        }
        return "%" + value.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
