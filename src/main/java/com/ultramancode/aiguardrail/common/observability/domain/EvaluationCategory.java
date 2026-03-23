package com.ultramancode.aiguardrail.common.observability.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 평가 주체 분류 (Human vs LLM)
 */
@Getter
@RequiredArgsConstructor
public enum EvaluationCategory {
    HUMAN("human"),
    LLM("llm");

    private final String value;
}
