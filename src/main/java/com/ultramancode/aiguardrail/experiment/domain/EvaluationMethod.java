package com.ultramancode.aiguardrail.experiment.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum EvaluationMethod {
    EXACT_MATCH("exact_match"),
    CONTAINS("contains"),
    LLM_JUDGE("llm_judge");

    private final String value;

    public static EvaluationMethod fromValue(String value) {
        return Arrays.stream(values())
                .filter(v -> v.value.equalsIgnoreCase(value))
                .findFirst()
                .orElse(EXACT_MATCH); // Default fallback
    }
}
