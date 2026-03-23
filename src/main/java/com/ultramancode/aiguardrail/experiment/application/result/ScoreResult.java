package com.ultramancode.aiguardrail.experiment.application.result;

import lombok.Builder;

/**
 * 점수 조회 결과 DTO
 * - Langfuse API에서 조회한 개별 점수 데이터
 */
@Builder
public record ScoreResult(
        String id,
        String traceId,
        String name,
        double value,
        String comment,
        Long createdAtEpochMillis
) {
}
