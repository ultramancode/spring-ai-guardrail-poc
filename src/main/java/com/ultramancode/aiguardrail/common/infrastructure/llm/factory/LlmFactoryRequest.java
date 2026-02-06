package com.ultramancode.aiguardrail.common.infrastructure.llm.factory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 생성 요청 DTO
 * Factory에 전달되어 동적으로 ChatModel을 생성하는 데 사용됩니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmFactoryRequest {

    /**
     * LLM 벤더 (gemini, ollama, openai)
     */
    private String vendor;

    /**
     * 모델명 (예: gemini-2.0-flash, gpt-4o)
     */
    private String model;

    /**
     * 온도 설정 (0.0 ~ 1.0)
     */
    private Double temperature;

    /**
     * 동적 API Key (null이면 기본값 사용)
     */
    private String apiKey;

    /**
     * 커스텀 Base URL (Ollama 등)
     */
    private String baseUrl;
}
