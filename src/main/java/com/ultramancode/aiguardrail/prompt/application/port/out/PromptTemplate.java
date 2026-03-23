package com.ultramancode.aiguardrail.prompt.application.port.out;

/**
 * 도메인 경계를 넘나들 때 사용하는 공용 프롬프트 조회 결과
 */
public record PromptTemplate(String content, String name, int version) {
}
