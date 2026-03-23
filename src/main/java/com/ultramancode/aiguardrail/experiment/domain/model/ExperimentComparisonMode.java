package com.ultramancode.aiguardrail.experiment.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 실험 결과 비교 모드.
 */
@Getter
@RequiredArgsConstructor
public enum ExperimentComparisonMode {
    MASKED_ONLY("masked_only"),
    DETOKENIZED_ONLY("detokenized_only"),
    MASKED_THEN_DETOKENIZED("masked_then_detokenized");

    private final String value;

    public static ExperimentComparisonMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return MASKED_ONLY;
        }

        for (ExperimentComparisonMode mode : ExperimentComparisonMode.values()) {
            if (mode.getValue().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }

        String supported = String.join(
                ", ",
                Arrays.stream(ExperimentComparisonMode.values())
                        .map(ExperimentComparisonMode::getValue)
                        .toList()
        );
        throw new IllegalArgumentException(
                "Unsupported evaluation.comparisonMode: " + value + ". Supported values: " + supported
        );
    }
}
