package com.ultramancode.aiguardrail.infrastructure.guardrail.pii;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.Duration;
import reactor.util.retry.Retry;

/**
 * Client for Microsoft Presidio Analyzer (NLP/AI-based PII detection).
 */
@Slf4j
@Service
public class PresidioClient {

    /**
     * Default confidence score when Presidio API doesn't return a score.
     * This is a fallback value; actual API responses should include their own scores.
     */
    public static final double DEFAULT_PRESIDIO_SCORE = 0.85;

    private final WebClient webClient;

    public PresidioClient(@Value("${presidio.analyzer.url:http://localhost:5001}") String analyzerUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(analyzerUrl)
                .build();
    }

    @SuppressWarnings("unchecked")
    public List<PhileasScanner.PiiSpan> analyze(String text) {
        try {
            log.info("[PRESIDIO] Requesting analysis for text: \"{}\"", text);

            List<Map<String, Object>> results = webClient.post()
                    .uri("/analyze")
                    .bodyValue(Map.of(
                            "text", text,
                            "language", "ko",
                            "entities", List.of("PERSON", "LOCATION", "ORG")
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

            // Map Presidio API results to PiiSpan with score for priority-based deduplication
            List<PhileasScanner.PiiSpan> spans = results.stream()
                    .map(res -> new PhileasScanner.PiiSpan(
                            (String) res.get("entity_type"),
                            (Integer) res.get("start"),
                            (Integer) res.get("end"),
                            text.substring((Integer) res.get("start"), (Integer) res.get("end")),
                            "PRESIDIO",
                            // Use actual model confidence score, fallback to default if not provided
                            res.get("score") != null ? ((Number) res.get("score")).doubleValue() : DEFAULT_PRESIDIO_SCORE)
                    )
                    .collect(Collectors.toList());

            spans.forEach(span -> 
                log.info("[PII-SCAN] Presidio Detected: [{}] - \"{}\"", span.type(), span.text())
            );

            return spans;
        } catch (Exception e) {
            log.error("[PRESIDIO] Error communicating with analyzer: {}", e.getMessage());
            return List.of();
        }
    }
}
