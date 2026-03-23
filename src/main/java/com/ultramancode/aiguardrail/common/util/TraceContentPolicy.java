package com.ultramancode.aiguardrail.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Function;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TraceContentPolicy {

    public static String resolve(String rawContent, boolean traceRawContent, Function<String, String> tokenizer) {
        if (rawContent == null) {
            return "";
        }

        if (traceRawContent) {
            return rawContent;
        }

        if (tokenizer == null) {
            return "";
        }

        String masked = tokenizer.apply(rawContent);
        if (masked == null) {
            return "";
        }

        return masked;
    }
}
