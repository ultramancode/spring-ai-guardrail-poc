package com.ultramancode.aiguardrail.multimodal.presentation.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vision API 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionResponse {

    /**
     * LLM 응답
     */
    private String answer;

    /**
     * Langfuse Trace ID (대시보드에서 확인용)
     */
    private String traceId;

    /**
     * 처리 방식 (DIRECT_VISION / PDF_PARSED)
     */
    private String processingMode;

    /**
     * 원본 파일명
     */
    private String originalFileName;

    /**
     * 추출된 텍스트 (PDF 파싱 시)
     */
    private String extractedText;
}
