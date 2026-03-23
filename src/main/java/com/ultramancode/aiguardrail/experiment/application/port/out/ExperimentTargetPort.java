package com.ultramancode.aiguardrail.experiment.application.port.out;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import org.springframework.lang.Nullable;

/**
 * 실험 타겟(이미지/PDF 등)을 실제 분석 유스케이스에 연결하는 포트입니다.
 */
public interface ExperimentTargetPort {

    ExperimentTargetResult execute(
            @Nullable String targetType,
            String question,
            @Nullable AttachmentFile file,
            @Nullable String traceId,
            @Nullable String systemPrompt,
            @Nullable String vendor,
            @Nullable String model
    );

    record ExperimentTargetResult(
            String answer,
            String maskedAnswer,
            @Nullable String observationId
    ) {
    }
}

