package com.ultramancode.aiguardrail.experiment.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 실험 실행 모드.
 */
@Getter
@RequiredArgsConstructor
public enum ExperimentMode {
    /**
     * FULL_WORKFLOW를 거치지 않고 target 실행 경로만 테스트한다.
     */
    TARGET_ONLY("TARGET_ONLY"),

    /**
     * 전체 워크플로우를 테스트한다.
     */
    FULL_WORKFLOW("FULL_WORKFLOW");

    private final String value;

    public static ExperimentMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return FULL_WORKFLOW;
        }

        String normalizedValue = value.trim();
        if ("PROMPT_ONLY".equalsIgnoreCase(normalizedValue)) {
            return TARGET_ONLY;
        }

        for (ExperimentMode mode : ExperimentMode.values()) {
            if (mode.getValue().equalsIgnoreCase(normalizedValue)) {
                return mode;
            }
        }

        throw new IllegalArgumentException("Unsupported experiment mode: " + value);
    }

    public boolean isFullWorkflow() {
        if (this == FULL_WORKFLOW) {
            return true;
        }
        return false;
    }
}
