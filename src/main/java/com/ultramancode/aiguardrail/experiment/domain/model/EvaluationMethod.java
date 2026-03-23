package com.ultramancode.aiguardrail.experiment.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 실험 결과 평가 방식.
 */
@Getter
@RequiredArgsConstructor
public enum EvaluationMethod {
    CONTAINS("contains"),
    EXACT_MATCH("exact_match"),
    LLM_JUDGE("llm_judge");

    private final String value;

    public static EvaluationMethod fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Evaluation method must not be blank.");
        }

        String normalizedValue = value.trim();
        for (EvaluationMethod method : EvaluationMethod.values()) {
            if (method.getValue().equalsIgnoreCase(normalizedValue)) {
                return method;
            }
        }

        String supported = String.join(
                ", ",
                Arrays.stream(EvaluationMethod.values())
                        .map(EvaluationMethod::getValue)
                        .toList()
        );
        throw new IllegalArgumentException(
                "Unsupported evaluation.type: " + normalizedValue + ". Supported values: " + supported
        );
    }
}
