package com.ultramancode.aiguardrail.prompt.domain;

/**
 * 프롬프트 제공자(예: Langfuse)로부터 조회한 프롬프트 정보
 */
public record FetchedPrompt(String content, String name, int version) {
}
