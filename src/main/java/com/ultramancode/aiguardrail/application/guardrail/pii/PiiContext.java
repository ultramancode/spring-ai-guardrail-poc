package com.ultramancode.aiguardrail.application.guardrail.pii;

import lombok.Getter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds the mapping between PII tokens and their original values for a single request.
 */
@Getter
public class PiiContext {
    private final Map<String, String> tokenToOriginal = new ConcurrentHashMap<>();
    private final Map<String, String> originalToToken = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> typeCounters = new ConcurrentHashMap<>();

    public String getOrCreateToken(String originalValue, String piiType) {
        return originalToToken.computeIfAbsent(originalValue, v -> {
            int count = typeCounters.computeIfAbsent(piiType, t -> new AtomicInteger(0)).incrementAndGet();
            String token = String.format("[%s_%d]", piiType.toUpperCase(), count);
            tokenToOriginal.put(token, v);
            return token;
        });
    }

    public String getOriginal(String token) {
        return tokenToOriginal.get(token);
    }
    
    public boolean containsToken(String token) {
        return tokenToOriginal.containsKey(token);
    }
}
