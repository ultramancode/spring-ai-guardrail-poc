package com.ultramancode.aiguardrail.common.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

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
     * LLM 벤더 (google, ollama)
     */
    @Nullable
    private String vendor;

    /**
     * 모델명 (예: gemini-2.0-flash, gpt-oss:20b)
     */
    @Nullable
    private String model;

    /**
     * temperature 설정 (0.0 ~ 1.0)
     */
    @Nullable
    private Double temperature;

    /**
     * API Key (null이면 기본값 사용)
     */
    @Nullable
    private String apiKey;

    /**
     * 커스텀 Base URL (Ollama 등)
     */
    @Nullable
    private String baseUrl;
}

