package com.ultramancode.aiguardrail.experiment.application.result;

import lombok.Builder;

/**
 * Raw Score Record (Data Transfer)
 * - Langfuse API에서 조회한 개별 점수 데이터
 */
@Builder
public record ScoreResult(
        String id,
        String traceId,
        String name,
        double value,
        String comment
) {
}
