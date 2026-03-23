package com.ultramancode.aiguardrail.common.observability.domain;

import lombok.Builder;
import org.springframework.lang.Nullable;

@Builder
public record GenerationAttachment(
        String fileName,
        String contentType,
        byte[] contentBytes,
        @Nullable String extractedText
) {
}
