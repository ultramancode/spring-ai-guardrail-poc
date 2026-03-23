package com.ultramancode.aiguardrail.multimodal.presentation.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 멀티모달 분석 API 응답 DTO입니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultimodalResponse {

    /**
     * LLM 응답
     */
    private String answer;

    /**
     * 요청 처리 시점 Trace ID
     */
    private String traceId;

    /**
     * 처리 방식 (ANALYZE_IMAGE / ANALYZE_PDF)
     */
    private String processingMode;

    /**
     * 원본 파일명
     */
    private String originalFileName;
}
