package com.ultramancode.aiguardrail.multimodal.application.port.in;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import org.springframework.lang.Nullable;

/**
 * 외부 모듈에서 멀티모달 타겟 분석을 호출하기 위한 파사드 유스케이스입니다.
 */
public interface MultimodalTargetFacadeUseCase {

    MultimodalTargetFacadeResult analyze(MultimodalTargetFacadeCommand command);

    record MultimodalTargetFacadeCommand(
            String targetType,
            String question,
            @Nullable AttachmentFile file,
            @Nullable String traceId,
            @Nullable String systemPrompt,
            @Nullable String vendor,
            @Nullable String model
    ) {
    }

    record MultimodalTargetFacadeResult(
            String answer,
            String maskedAnswer,
            @Nullable String observationId
    ) {
    }
}
