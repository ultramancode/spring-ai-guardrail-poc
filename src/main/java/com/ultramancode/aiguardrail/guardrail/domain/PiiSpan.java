package com.ultramancode.aiguardrail.guardrail.domain;

/**
 * 텍스트 내에서 탐지된 PII(개인정보) 구간을 나타냅니다.
 * 인프라 계층의 스캐너와 애플리케이션 서비스 간의 통신에 사용됩니다.
 */
public record PiiSpan(
        String type,
        int start,
        int end,
        String text,
        String source,
        double score
) {
}
