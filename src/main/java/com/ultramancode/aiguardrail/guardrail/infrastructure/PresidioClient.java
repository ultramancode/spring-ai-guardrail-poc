package com.ultramancode.aiguardrail.guardrail.infrastructure;

import com.ultramancode.aiguardrail.common.util.StringValueUtils;
import com.ultramancode.aiguardrail.guardrail.application.port.out.PiiAnalyzerPort;
import com.ultramancode.aiguardrail.guardrail.domain.PiiSpan;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Microsoft Presidio Analyzer(NLP/AI 기반 PII 탐지)를 위한 클라이언트입니다.
 */
@Slf4j
@Service
public class PresidioClient implements PiiAnalyzerPort {

    /**
     * Presidio API가 점수를 반환하지 않을 때의 기본 신뢰 점수입니다.
     * 이는 대체(Fallback) 값이며, 실제 API 응답에는 자체 점수가 포함되어야 합니다.
     */
    public static final double DEFAULT_PRESIDIO_SCORE = 0.85;
    private static final String REQ_TEXT = "text";
    private static final String REQ_LANG = "language";
    private static final String REQ_ENTITIES = "entities";
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_MAX_RETRIES = 1;
    private static final int DEFAULT_RETRY_BACKOFF_MS = 300;
    private final WebClient webClient;
    private final String piiLanguage;
    private final List<String> piiEntities;
    private final boolean traceRawContent;
    private final boolean presidioFailOpen;
    private final int requestTimeoutMs;
    private final int maxRetries;
    private final int retryBackoffMs;

    public PresidioClient(
            @Value("${presidio.analyzer.url}") String analyzerUrl,
            @Value("${guardrail.pii.language:ko}") String piiLanguage,
            @Value("${guardrail.pii.entities:PERSON,LOCATION,ORG}") List<String> piiEntities,
            @Value("${guardrail.pii.trace-raw-content:false}") boolean traceRawContent,
            @Value("${guardrail.pii.presidio.fail-open:false}") boolean presidioFailOpen,
            @Value("${guardrail.pii.presidio.timeout-ms:" + DEFAULT_TIMEOUT_MS + "}") int requestTimeoutMs,
            @Value("${guardrail.pii.presidio.max-retries:" + DEFAULT_MAX_RETRIES + "}") int maxRetries,
            @Value("${guardrail.pii.presidio.retry-backoff-ms:" + DEFAULT_RETRY_BACKOFF_MS + "}") int retryBackoffMs) {
        this.webClient = WebClient.builder()
                .baseUrl(analyzerUrl)
                .build();
        this.piiLanguage = piiLanguage;
        this.piiEntities = piiEntities;
        this.traceRawContent = traceRawContent;
        this.presidioFailOpen = presidioFailOpen;
        this.requestTimeoutMs = normalizePositiveOrDefault(requestTimeoutMs, DEFAULT_TIMEOUT_MS, "timeout-ms");
        this.maxRetries = normalizeNonNegativeOrDefault(maxRetries, DEFAULT_MAX_RETRIES, "max-retries");
        this.retryBackoffMs = normalizePositiveOrDefault(retryBackoffMs, DEFAULT_RETRY_BACKOFF_MS, "retry-backoff-ms");
    }

    @SuppressWarnings("unchecked")
    public List<PiiSpan> analyze(String text) {
        try {
            Span.current().setAttribute("guardrail.pii.presidio.error", false);

            if (traceRawContent) {
                log.info("[PRESIDIO] Requesting analysis for text: \"{}\"", text);
            } else {
                int textLength = text != null ? text.length() : 0;
                log.info("[PRESIDIO] Requesting analysis. textLength={}", textLength);
            }

            Mono<List<Map<String, Object>>> responseMono = webClient.post()
                    .uri("/analyze")
                    .bodyValue(Map.of(
                            REQ_TEXT, text,
                            REQ_LANG, this.piiLanguage,
                            REQ_ENTITIES, this.piiEntities
                    ))
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .map(map -> (Map<String, Object>) map)
                    .collectList()
                    .timeout(Duration.ofMillis(requestTimeoutMs));

            if (maxRetries > 0) {
                responseMono = responseMono.retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(retryBackoffMs)));
            }

            List<Map<String, Object>> results = responseMono.block();

            if (results == null || results.isEmpty()) {
                log.info("[PRESIDIO] No entities detected or empty response.");
                return List.of();
            }

            if (traceRawContent) {
                log.info("[PRESIDIO] Raw Response: {}", results);
            } else {
                log.debug("[PRESIDIO] Detection result count={}", results.size());
            }

            List<PiiSpan> spans = toPiiSpans(results, text);

            for (PiiSpan span : spans) {
                if (traceRawContent) {
                    log.info("[PII-SCAN] Presidio Detected: [{}] - \"{}\"", span.type(), span.text());
                } else {
                    log.info(
                            "[PII-SCAN] Presidio Detected: [{}] [{}~{}] source={}",
                            span.type(),
                            span.start(),
                            span.end(),
                            span.source()
                    );
                }
            }

            return spans;
        } catch (RuntimeException e) {
            return handleAnalyzeFailure(e);
        }
    }

    private List<PiiSpan> handleAnalyzeFailure(Exception e) {
        Span.current().setAttribute("guardrail.pii.presidio.error", true);
        Span.current().setAttribute("guardrail.pii.presidio.fail_open", presidioFailOpen);

        if (presidioFailOpen) {
            log.error("[PRESIDIO] Error communicating with analyzer (fail-open): {}", e.getMessage(), e);
            return List.of();
        }

        throw new IllegalStateException("Presidio analyzer call failed", e);
    }

    private List<PiiSpan> toPiiSpans(List<Map<String, Object>> results, String text) {
        List<PiiSpan> spans = new ArrayList<>();
        for (Map<String, Object> result : results) {
            PiiSpan span = toPiiSpanSafely(result, text);
            if (span != null) {
                spans.add(span);
            }
        }
        return spans;
    }

    private PiiSpan toPiiSpanSafely(Map<String, Object> payload, String text) {
        String entityType = StringValueUtils.asNonBlankString(payload.get("entity_type"));
        Integer rawStart = asInteger(payload.get("start"));
        Integer rawEnd = asInteger(payload.get("end"));

        if (entityType == null || rawStart == null || rawEnd == null) {
            log.warn("[PRESIDIO] Skipping invalid payload: {}", payload);
            return null;
        }

        SpanPosition resolvedPosition = resolveSpanPosition(rawStart, rawEnd, text);
        if (resolvedPosition == null) {
            log.warn(
                    "[PRESIDIO] Skipping out-of-range span: start={}, end={}, textLength={}",
                    rawStart,
                    rawEnd,
                    text.length()
            );
            return null;
        }

        double score = DEFAULT_PRESIDIO_SCORE;
        Object rawScore = payload.get("score");
        if (rawScore instanceof Number number) {
            score = number.doubleValue();
        }

        String spanText = text.substring(resolvedPosition.start(), resolvedPosition.end());
        return new PiiSpan(
                entityType,
                resolvedPosition.start(),
                resolvedPosition.end(),
                spanText,
                "PRESIDIO",
                score
        );
    }

    private SpanPosition resolveSpanPosition(int rawStart, int rawEnd, String text) {
        int textLength = text.length();
        if (isValidSpan(rawStart, rawEnd, textLength)) {
            return new SpanPosition(rawStart, rawEnd);
        }

        int codePointLength = text.codePointCount(0, textLength);
        if (!isValidSpan(rawStart, rawEnd, codePointLength)) {
            return null;
        }

        try {
            int charStart = text.offsetByCodePoints(0, rawStart);
            int charEnd = text.offsetByCodePoints(0, rawEnd);
            if (!isValidSpan(charStart, charEnd, textLength)) {
                return null;
            }
            return new SpanPosition(charStart, charEnd);
        } catch (RuntimeException e) {
            log.warn("[PRESIDIO] Failed to convert codepoint span: {}", e.getMessage(), e);
            return null;
        }
    }

    private boolean isValidSpan(int start, int end, int maxLength) {
        if (start < 0) {
            return false;
        }
        if (end > maxLength) {
            return false;
        }
        if (start >= end) {
            return false;
        }
        return true;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    private int normalizePositiveOrDefault(int configuredValue, int defaultValue, String keyName) {
        if (configuredValue > 0) {
            return configuredValue;
        }

        log.warn(
                "[PRESIDIO] Invalid guardrail.pii.presidio.{}: {}. Fallback to {}.",
                keyName,
                configuredValue,
                defaultValue
        );
        return defaultValue;
    }

    private int normalizeNonNegativeOrDefault(int configuredValue, int defaultValue, String keyName) {
        if (configuredValue >= 0) {
            return configuredValue;
        }

        log.warn(
                "[PRESIDIO] Invalid guardrail.pii.presidio.{}: {}. Fallback to {}.",
                keyName,
                configuredValue,
                defaultValue
        );
        return defaultValue;
    }

    private record SpanPosition(int start, int end) {
    }
}
