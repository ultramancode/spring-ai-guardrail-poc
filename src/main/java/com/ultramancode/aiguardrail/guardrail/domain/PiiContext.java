package com.ultramancode.aiguardrail.guardrail.domain;

import lombok.Getter;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 단일 요청 내에서 PII 토큰과 원본 값 사이의 매핑 정보를 유지합니다.
 */
@Getter
public class PiiContext {
    private static final String TOKEN_KEY_SEPARATOR = "\u0000";
    private static final String UNKNOWN_PII_TYPE = "UNKNOWN";

    private final Map<String, String> tokenToOriginal = new ConcurrentHashMap<>();
    private final Map<String, String> originalToToken = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> typeCounters = new ConcurrentHashMap<>();
    // 동일한 요청 내에서 중복되는 입력에 대한 불필요한 분석을 방지하기 위한 캐시
    private final Map<String, String> analysisCache = new ConcurrentHashMap<>();

    public String getOrCreateToken(String originalValue, String piiType) {
        String normalizedOriginal = normalizeOriginalValue(originalValue);
        String normalizedPiiType = normalizePiiType(piiType);
        String tokenLookupKey = buildTokenLookupKey(normalizedPiiType, normalizedOriginal);

        return originalToToken.computeIfAbsent(tokenLookupKey, ignored -> {
            int count = typeCounters.computeIfAbsent(normalizedPiiType, t -> new AtomicInteger(0)).incrementAndGet();
            String token = String.format("[%s_%d]", normalizedPiiType, count);
            tokenToOriginal.put(token, normalizedOriginal);
            return token;
        });
    }

    private String normalizeOriginalValue(String originalValue) {
        if (originalValue == null) {
            return "";
        }
        return originalValue;
    }

    private String normalizePiiType(String piiType) {
        if (piiType == null || piiType.isBlank()) {
            return UNKNOWN_PII_TYPE;
        }
        return piiType.trim().toUpperCase(Locale.ROOT);
    }

    private String buildTokenLookupKey(String piiType, String originalValue) {
        return piiType + TOKEN_KEY_SEPARATOR + originalValue;
    }

}
