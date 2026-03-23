package com.ultramancode.aiguardrail.common.observability.command;

import com.ultramancode.aiguardrail.common.observability.UsageResult;
import com.ultramancode.aiguardrail.common.observability.domain.GenerationAttachment;
import lombok.Builder;
import lombok.Getter;
import org.springframework.lang.Nullable;

/**
 * LLM 생성 작업 기록을 위한 커맨드 객체
 */
@Getter
@Builder
public class RecordGenerationCommand {
    @Nullable
    private final String traceId;
    private final String name;
    private final String modelName;
    private final String input;
    private final String output;

    @Nullable
    private final GenerationAttachment attachment;

    @Nullable
    private final UsageResult usage;

    @Nullable
    private final String promptName;

    private final long startTime;
    private final long endTime;
}
