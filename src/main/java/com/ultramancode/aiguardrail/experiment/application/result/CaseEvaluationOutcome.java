package com.ultramancode.aiguardrail.experiment.application.result;

/**
 * 테스트 케이스 단위 비교 평가 결과입니다.
 */
public record CaseEvaluationOutcome(
        EvaluationMatchResult matchResult,
        String comparedActual,
        String comparedValueLabel
) {
}
