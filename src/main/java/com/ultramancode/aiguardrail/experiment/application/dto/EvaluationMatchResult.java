package com.ultramancode.aiguardrail.experiment.application.dto;

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
