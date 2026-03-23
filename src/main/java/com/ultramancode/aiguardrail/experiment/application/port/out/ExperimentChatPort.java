package com.ultramancode.aiguardrail.experiment.application.port.out;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import org.springframework.lang.Nullable;

/**
 * FULL_WORKFLOW 실험에서 채팅 실행을 추상화한 포트.
 */
public interface ExperimentChatPort {

    ExperimentChatResult chat(ExperimentChatRequest request);

    record ExperimentChatRequest(
            String text,
            @Nullable AttachmentFile file,
            @Nullable String vendor,
            @Nullable String model,
            @Nullable String targetGuardrail,
            @Nullable String systemPrompt
    ) {
    }

    record ExperimentChatResult(
            String output,
            @Nullable String observationId
    ) {
    }
}

