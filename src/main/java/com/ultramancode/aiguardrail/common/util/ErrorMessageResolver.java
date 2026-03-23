package com.ultramancode.aiguardrail.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessageResolver {

    public static String resolve(RuntimeException e, String fallback) {
        if (e == null) {
            return fallback;
        }

        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            if (fallback != null) {
                if (!fallback.isBlank()) {
                    return fallback;
                }
            }
            return e.getClass().getSimpleName();
        }

        return message;
    }
}
