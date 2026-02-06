package com.ultramancode.aiguardrail.common.observability;

import lombok.Builder;

/**
 * 토큰 사용량 정보 (Framework Agnostic)
 */
@Builder
public record UsageResult(
        long promptTokens,
        long completionTokens,
        long totalTokens
) {
}
