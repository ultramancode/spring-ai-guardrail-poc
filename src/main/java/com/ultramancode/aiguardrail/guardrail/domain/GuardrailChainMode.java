package com.ultramancode.aiguardrail.guardrail.domain;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 실험/채팅 경로에서 적용할 가드레일 체인 모드.
 */
public enum GuardrailChainMode {
    INJECTION("injection"),
    OUTPUT("output"),
    FULL("full");

    private final String value;

    GuardrailChainMode(String value) {
        this.value = value;
    }

    public static GuardrailChainMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("guardrail mode must not be blank");
        }

        for (GuardrailChainMode mode : GuardrailChainMode.values()) {
            if (mode.value.equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }

        String supported = Arrays.stream(GuardrailChainMode.values())
                .map(GuardrailChainMode::getValue)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Unsupported targetGuardrail: " + value + ". Supported values: " + supported);
    }

    public static GuardrailChainMode fromValueOrDefault(String value, GuardrailChainMode defaultMode) {
        if (value == null || value.isBlank()) {
            return defaultMode;
        }
        return fromValue(value);
    }

    public String getValue() {
        return value;
    }
}
