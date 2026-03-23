package com.ultramancode.aiguardrail.multimodal.application.model;

import com.ultramancode.aiguardrail.common.observability.domain.GenerationAttachment;
import lombok.Builder;
import org.springframework.core.io.Resource;

/**
 * LLM 실행에 필요한 컨텍스트 데이터
 */
@Builder
public record LlmExecutionData(
        String prompt,
        String systemPrompt,
        String vendor,
        String model,
        GenerationAttachment attachment,
        Resource mediaSource,
        String mediaType
) {
}
