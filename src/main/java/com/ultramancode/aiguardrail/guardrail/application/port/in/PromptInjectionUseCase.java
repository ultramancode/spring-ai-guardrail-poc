package com.ultramancode.aiguardrail.guardrail.application.port.in;

/**
 * 프롬프트 인젝션(Prompt Injection) 공격을 탐지하는 유스케이스 인터페이스입니다.
 * 모델에 전달되기 전의 사용자 입력이 보안 정책상 안전한지 검사합니다.
 */
public interface PromptInjectionUseCase {
    /**
     * 입력 텍스트의 보안 위험을 검사합니다.
     * 인젝션 위험이 감지될 경우 예외를 발생시키거나 보안 조치를 취합니다.
     *
     * @param input   검사할 입력 텍스트
     * @param traceId 추적을 위한 트레이스 ID
     */
    void checkSecurity(String input, String traceId);
}
