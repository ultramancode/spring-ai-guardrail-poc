package com.ultramancode.aiguardrail.experiment.application.result;

import lombok.Builder;

/**
 * 평가 매칭 결과
 */
@Builder
public record EvaluationMatchResult(
        boolean match,
        double score,
        String reason
) {
}
