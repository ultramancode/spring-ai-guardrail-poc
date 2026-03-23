package com.ultramancode.aiguardrail.common.observability.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 분석/채팅 처리 모드입니다.
 */
@Getter
@RequiredArgsConstructor
public enum AnalysisMode {
    CHAT_TEXT("CHAT_TEXT"),
    ANALYZE_IMAGE("ANALYZE_IMAGE"),
    ANALYZE_PDF("ANALYZE_PDF");

    private final String value;
}
