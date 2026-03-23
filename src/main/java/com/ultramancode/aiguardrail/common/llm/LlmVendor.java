package com.ultramancode.aiguardrail.common.llm;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * LLM 벤더 종류 Enum
 */
@Getter
@RequiredArgsConstructor
public enum LlmVendor {
    GOOGLE,
    OLLAMA;

    public String getValue() {
        return name().toLowerCase();
    }
}
