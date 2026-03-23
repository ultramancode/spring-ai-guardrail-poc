package com.ultramancode.aiguardrail.common.observability.command;

import lombok.Builder;
import org.springframework.lang.Nullable;

@Builder
public record RecordScoreCommand(
        String traceId,
        String scoreName,
        double value,
        @Nullable String comment,
        @Nullable String observationId
) {
}
