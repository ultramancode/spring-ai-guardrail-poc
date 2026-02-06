package com.ultramancode.aiguardrail.multimodal.domain;

import lombok.Builder;

/**
 * 생성 모델(LLM) 입력에 포함된 첨부 파일 정보
 * (PDF, 이미지 등 멀티모달 입력 공통화)
 */
@Builder
public record GenerationAttachment(
        String fileName,
        String contentType, // MIME type (application/pdf, image/png, etc.)
        byte[] contentBytes,
        String extractedText // Optional (PDF 등 텍스트 추출이 가능한 경우)
) {
}
