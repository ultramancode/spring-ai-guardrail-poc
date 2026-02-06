package com.ultramancode.aiguardrail.guardrail.infrastructure;

import com.ultramancode.aiguardrail.guardrail.domain.PiiSpan;
import com.ultramancode.aiguardrail.guardrail.application.port.out.PiiAnalyzerPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import reactor.util.retry.Retry;

/**
 * Client for Microsoft Presidio Analyzer (NLP/AI-based PII detection).
 */
@Slf4j
@Service
public class PresidioClient implements PiiAnalyzerPort {

    /**
     * Default confidence score when Presidio API doesn't return a score.
     * This is a fallback value; actual API responses should include their own scores.
     */
    public static final double DEFAULT_PRESIDIO_SCORE = 0.85;

    private final WebClient webClient;
    private final String piiLanguage;
    private final List<String> piiEntities;

    public PresidioClient(
            @Value("${presidio.analyzer.url}") String analyzerUrl,
            @Value("${guardrail.pii.language:ko}") String piiLanguage,
            @Value("${guardrail.pii.entities:PERSON,LOCATION,ORG}") List<String> piiEntities) {
        this.webClient = WebClient.builder()
                .baseUrl(analyzerUrl)
                .build();
        this.piiLanguage = piiLanguage;
        this.piiEntities = piiEntities;
    }

    private static final String REQ_TEXT = "text";
    private static final String REQ_LANG = "language";
    private static final String REQ_ENTITIES = "entities";

    @SuppressWarnings("unchecked")
    public List<PiiSpan> analyze(String text) {
        try {
            log.info("[PRESIDIO] Requesting analysis for text: \"{}\"", text);

            List<Map<String, Object>> results = webClient.post()
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
                    .timeout(Duration.ofSeconds(30))
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(500)))
                    .block();

            if (results == null || results.isEmpty()) {
                log.info("[PRESIDIO] No entities detected or empty response.");
                return List.of();
            }

            log.info("[PRESIDIO] Raw Response: {}", results);

            List<PiiSpan> spans = toPiiSpans(results, text);

            spans.forEach(span ->
                    log.info("[PII-SCAN] Presidio Detected: [{}] - \"{}\"", span.type(), span.text())
            );

            return spans;
        } catch (Exception e) {
            log.error("[PRESIDIO] Error communicating with analyzer: {}", e.getMessage());
            return List.of();
        }
    }

    private List<PiiSpan> toPiiSpans(List<Map<String, Object>> results, String text) {
        return results.stream()
                .map(res -> new PiiSpan(
                        (String) res.get("entity_type"),
                        (Integer) res.get("start"),
                        (Integer) res.get("end"),
                        text.substring((Integer) res.get("start"), (Integer) res.get("end")),
                        "PRESIDIO",
                        res.get("score") != null ? ((Number) res.get("score")).doubleValue() : DEFAULT_PRESIDIO_SCORE)
                )
                .collect(Collectors.toList());
    }
}
