package com.ultramancode.aiguardrail.guardrail.application.command;

import org.springframework.web.multipart.MultipartFile;
import lombok.Builder;
import lombok.Getter;

/**
 * PII 보호 채팅을 위한 명령 객체
 */
@Getter
@Builder
public class PiiChatCommand {
    private final String text;
    private final boolean useMcp;
    private final MultipartFile file;

    /**
     * LLM 벤더 (gemini, ollama, openai)
     * null이면 기본값(gemini) 사용
     */
    private final String vendor;

    /**
     * 모델명 (예: gemini-2.0-flash, gpt-4o, llama3)
     * null이면 프로바이더의 기본값 사용
     */
    private final String model;
}
