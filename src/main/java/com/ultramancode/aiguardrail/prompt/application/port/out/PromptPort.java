package com.ultramancode.aiguardrail.prompt.application.port.out;

import java.util.Optional;

/**
 * 시스템 전반에서 필요한 프롬프트를 조회하기 위한 포트 인터페이스
 */
public interface PromptPort {
    /**
     * 프롬프트 명으로 최신 프롬프트 내용을 조회합니다.
     * 프롬프트가 없으면 Optional.empty()를 반환합니다.
     */
    Optional<PromptTemplate> fetchPrompt(String promptName);

    /**
     * 프롬프트 명으로 조회하고, 없거나 조회 실패한 경우 예외를 던집니다.
     */
    PromptTemplate fetchPromptOrThrow(String promptName);
}
