package com.ultramancode.aiguardrail.guardrail.application.domain;

/**
 * Langfuse에서 조회한 프롬프트 정보 (내용 + 버전)
 * - 버전을 추적해야 Generation과 Prompt가 링크됨.
 */
public record FetchedPrompt(String content, String name, int version) {
}
