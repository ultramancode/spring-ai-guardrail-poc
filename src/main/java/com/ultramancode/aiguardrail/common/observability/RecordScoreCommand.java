package com.ultramancode.aiguardrail.common.observability;

import lombok.Builder;

/**
 * 점수 기록 명령 (Command Object)
 * - Presentation(HTTP) 요청과 독립적인 순수 애플리케이션 데이터
 * - Moved to Common Observability as it is strictly for observability scoring
 */
@Builder
public record RecordScoreCommand(
        String traceId,
        String scoreName,
        double value,
        String comment,      // Optional
        String observationId // Optional
) {
}
