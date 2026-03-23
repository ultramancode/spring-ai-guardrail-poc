package com.ultramancode.aiguardrail.guardrail.application.support;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 토큰 문자열을 원문으로 복원하는 책임을 담당합니다.
 */
@Component
public class PiiDetokenizationSupport {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\[[A-Z_]+_\\d+\\]");

    public String detokenizeText(String text, Map<String, String> tokenToOriginal) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (tokenToOriginal == null || tokenToOriginal.isEmpty()) {
            return text;
        }

        Matcher matcher = TOKEN_PATTERN.matcher(text);
        StringBuilder restored = new StringBuilder();
        boolean matched = false;

        while (matcher.find()) {
            matched = true;
            String token = matcher.group();
            String original = tokenToOriginal.get(token);
            if (original == null) {
                matcher.appendReplacement(restored, Matcher.quoteReplacement(token));
            } else {
                matcher.appendReplacement(restored, Matcher.quoteReplacement(original));
            }
        }

        if (!matched) {
            return text;
        }

        matcher.appendTail(restored);
        return restored.toString();
    }

    public Object detokenizeRecursive(Object input, Function<String, String> detokenizeTextFunction) {
        if (input instanceof String str) {
            return detokenizeTextFunction.apply(str);
        }

        if (input instanceof Map<?, ?> map) {
            Map<Object, Object> restoredMap = new LinkedHashMap<>();
            map.forEach((key, value) -> restoredMap.put(key, detokenizeRecursive(value, detokenizeTextFunction)));
            return restoredMap;
        }

        if (input instanceof List<?> list) {
            return list.stream()
                    .map(value -> detokenizeRecursive(value, detokenizeTextFunction))
                    .toList();
        }

        return input;
    }
}
