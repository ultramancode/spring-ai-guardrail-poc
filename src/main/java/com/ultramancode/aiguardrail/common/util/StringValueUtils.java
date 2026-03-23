package com.ultramancode.aiguardrail.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StringValueUtils {

    public static String firstNonBlank(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }

        return trimmed;
    }

    public static String asNonBlankString(Object value) {
        if (value == null) {
            return null;
        }

        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }

        return text;
    }
}
