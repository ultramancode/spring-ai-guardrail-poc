package com.ultramancode.aiguardrail.guardrail.application.result;

import org.springframework.lang.Nullable;

/**
 * PII 채팅 처리 결과.
 */
public record PiiChatResult(
        String output,
        @Nullable String observationId
) {
    public static PiiChatResult of(String output, @Nullable String observationId) {
        return new PiiChatResult(output, observationId);
    }
}
