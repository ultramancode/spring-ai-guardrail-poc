package com.ultramancode.aiguardrail.experiment.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 실험 실행 시 사용할 가드레일 체인 모드.
 */
@Getter
@RequiredArgsConstructor
public enum ExperimentGuardrailMode {
    INJECTION("injection"),
    OUTPUT("output"),
    FULL("full");

    private final String value;

    public static ExperimentGuardrailMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Experiment guardrail mode must not be blank.");
        }

        String normalizedValue = value.trim();
        for (ExperimentGuardrailMode mode : ExperimentGuardrailMode.values()) {
            if (mode.getValue().equalsIgnoreCase(normalizedValue)) {
                return mode;
            }
        }

        String supported = Arrays.stream(ExperimentGuardrailMode.values())
                .map(ExperimentGuardrailMode::getValue)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        throw new IllegalArgumentException(
                "Unsupported experiment guardrail mode: " + normalizedValue + ". Supported values: " + supported
        );
    }

    public static ExperimentGuardrailMode fromValueOrDefault(String value, ExperimentGuardrailMode defaultMode) {
        if (value == null || value.isBlank()) {
            return defaultMode;
        }
        return fromValue(value);
    }
}
