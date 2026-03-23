package com.ultramancode.aiguardrail.common.llm.infrastructure.factory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Google GenAI (Gemini) 설정 프로퍼티
 */
@ConfigurationProperties(prefix = "spring.ai.google.genai")
public record GoogleLlmProperties(
        String apiKey,
        @DefaultValue Chat chat
) {
    public record Chat(
            @DefaultValue Options options
    ) {
        public record Options(
                @DefaultValue(Options.DEFAULT_MODEL) String model,
                @DefaultValue("0.7") double temperature
        ) {
            public static final String DEFAULT_MODEL = "gemini-2.0-flash";
        }
    }
}
