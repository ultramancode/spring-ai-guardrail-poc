package com.ultramancode.aiguardrail.common.llm.infrastructure.factory.config;

import com.ultramancode.aiguardrail.common.llm.LlmVendor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 전역 LLM 설정 프로퍼티
 * 기본 벤더 GOOGLE
 */
@ConfigurationProperties(prefix = "guardrail.llm")
public record LlmProperties(
        @DefaultValue("GOOGLE") LlmVendor defaultVendor
) {
}
