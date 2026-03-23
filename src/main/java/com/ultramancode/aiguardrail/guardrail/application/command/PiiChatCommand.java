package com.ultramancode.aiguardrail.guardrail.application.command;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import lombok.Builder;
import lombok.Getter;
import org.springframework.lang.Nullable;

/**
 * PII 보호 채팅 요청 명령 객체.
 */
@Getter
@Builder
public class PiiChatCommand {

    private final String text;

    @Nullable
    private final AttachmentFile file;

    /**
     * LLM 벤더 (google, ollama).
     * null이면 기본 벤더를 사용한다.
     */
    @Nullable
    private final String vendor;

    /**
     * 모델명 (예: gemini-2.0-flash, gpt-oss:20b, llama3).
     * null이면 벤더 기본 모델을 사용한다.
     */
    @Nullable
    private final String model;

    /**
     * 가드레일 체인 모드 (injection, output, full).
     * null이면 기본 체인(full)을 사용한다.
     */
    @Nullable
    private final String targetGuardrail;

    /**
     * 시스템 프롬프트 오버라이드 값.
     * null이면 기본 프롬프트(또는 prompt-name 조회)를 사용한다.
     */
    @Nullable
    private final String systemPrompt;
}
