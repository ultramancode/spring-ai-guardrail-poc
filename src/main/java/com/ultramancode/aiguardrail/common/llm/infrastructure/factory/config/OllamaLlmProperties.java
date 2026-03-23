package com.ultramancode.aiguardrail.common.llm.infrastructure.factory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ollama 설정 프로퍼티
 */
@ConfigurationProperties(prefix = "spring.ai.ollama")
public record OllamaLlmProperties(
        @DefaultValue(DEFAULT_BASE_URL) String baseUrl,
        @DefaultValue Chat chat
) {
    public static final String DEFAULT_BASE_URL = "http://localhost:11434";

    public record Chat(
            @DefaultValue Options options
    ) {
        public record Options(
                @DefaultValue(Options.DEFAULT_MODEL) String model,
                @DefaultValue("0.7") double temperature
        ) {
            public static final String DEFAULT_MODEL = "gpt-oss:20b";
        }
    }
}
