package com.ultramancode.aiguardrail.common.observability.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 관측 점수 타입
 */
@Getter
@RequiredArgsConstructor
public enum ScoreType {
    PROMPT_INJECTION_SAFETY("prompt-injection-pass"),
    EXPERIMENT_SCORE("experiment-score"),
    USER_FEEDBACK("user_feedback");

    private final String value;
}
