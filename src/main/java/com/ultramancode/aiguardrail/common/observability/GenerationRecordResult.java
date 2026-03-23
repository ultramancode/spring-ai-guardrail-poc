package com.ultramancode.aiguardrail.common.observability;

import org.springframework.lang.Nullable;

/**
 * Generation 기록 결과(Trace/Observation 식별자)입니다.
 */
public record GenerationRecordResult(
        @Nullable String traceId,
        @Nullable String observationId
) {
    public static GenerationRecordResult empty() {
        return new GenerationRecordResult(null, null);
    }
}
