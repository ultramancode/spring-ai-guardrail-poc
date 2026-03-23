package com.ultramancode.aiguardrail.common.observability.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Langfuse 관측 타입
 */
@Getter
@RequiredArgsConstructor
public enum ObservationType {
    GUARDRAIL("guardrail"),
    EVALUATOR("evaluator"),
    CHAIN("chain"),
    TOOL("tool");

    private final String value;
}
