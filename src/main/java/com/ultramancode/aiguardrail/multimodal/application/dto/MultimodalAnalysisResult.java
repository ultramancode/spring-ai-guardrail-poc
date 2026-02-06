package com.ultramancode.aiguardrail.multimodal.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 멀티모달 분석 결과 DTO (Application Layer)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultimodalAnalysisResult {

    /**
     * LLM 응답
     */
    private String answer;

    /**
     * Langfuse Trace ID
     */
    private String traceId;

    /**
     * 마스킹된 응답 (실험 평가용)
     */
    private String maskedAnswer;

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
