package com.ultramancode.aiguardrail.guardrail.application.service;

import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.guardrail.domain.PiiSpan;
import com.ultramancode.aiguardrail.guardrail.domain.PiiContext;
import com.ultramancode.aiguardrail.guardrail.domain.PiiContextHolder;
import com.ultramancode.aiguardrail.common.observability.ObservabilityConstants;
import com.ultramancode.aiguardrail.guardrail.application.port.out.PiiAnalyzerPort;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates PII detection and reversible tokenization.
 * <ul>
 *   <li>deduplication with Containment, Score-based priority, and Overlap filtering</li>
 *   <li>Hybrid detection using Phileas (Regex) + Presidio (AI/NER)</li>
 *   <li>Reversible tokenization for LLM-safe PII handling</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PiiService implements PiiUseCase {

    private final List<PiiAnalyzerPort> piiAnalyzers;
    private final ObservationRegistry observationRegistry;

    // @Observed(name = "pii.tokenize") 
    // 위 어노테이션 대신 아래처럼 manual Observation을 사용합니다. 
    // 이유는 랭퓨즈 UI에서 '방패(guardrail)' 아이콘을 띄우기 위해 
    // 'langfuse.observation_type'이라는 태그를 동적으로 주입해야 하기 때문입니다.
    public String tokenize(String text) {
        if (text == null || text.isBlank())
            return text;

        // [Optimization] Cache check BEFORE span creation
        PiiContext context = PiiContextHolder.getContext();
        if (context.getAnalysisCache().containsKey(text)) {
            log.debug("[PII-CACHE] Cache hit (No span created) for text: '{}'",
                    text.length() > 20 ? text.substring(0, 20) + "..." : text);
            return context.getAnalysisCache().get(text);
        }

        return Observation.createNotStarted("pii.tokenize", observationRegistry)
                .lowCardinalityKeyValue(ObservabilityConstants.LF_OBSERVATION_TYPE, ObservabilityConstants.LF_VAL_GUARDRAIL)
                .observe(() -> tokenizeInternal(text, context));
    }

    /**
     * Internal version that skips Micrometer Observation for recursive or
     * high-frequency calls.
     */
    public String tokenizeInternal(String text) {
        return tokenizeInternal(text, PiiContextHolder.getContext());
    }

    private String tokenizeInternal(String text, PiiContext context) {
        if (text == null || text.isBlank())
            return text;

        if (context.getAnalysisCache().containsKey(text)) {
            return context.getAnalysisCache().get(text);
        }

        List<PiiSpan> allSpans = new ArrayList<>();
        for (PiiAnalyzerPort analyzer : piiAnalyzers) {
            allSpans.addAll(analyzer.analyze(text));
        }

        // DEDUPLICATION ALGORITHM
        List<PiiSpan> filteredSpans = advancedDeduplication(allSpans);

        // 3. Sort by START INDEX (descending) for safe string replacement
        filteredSpans.sort(Comparator.comparingInt(PiiSpan::start).reversed());

        StringBuilder sb = new StringBuilder(text);

        for (PiiSpan span : filteredSpans) {
            String original = text.substring(span.start(), span.end());
            String token = context.getOrCreateToken(original, span.type());
            sb.replace(span.start(), span.end(), token);
        }

        String result = sb.toString();
        context.getAnalysisCache().put(text, result);
        return result;
    }

    /**
     * Deduplication algorithm for merging results from multiple PII detection engines.
     */
    private List<PiiSpan> advancedDeduplication(List<PiiSpan> spans) {
        // Step 1: Score 내림차순 정렬
        spans.sort(Comparator.comparingDouble(PiiSpan::score).reversed());

        List<PiiSpan> result = new ArrayList<>();

        for (PiiSpan candidate : spans) {
            // Step 2-1: Containment Check - 이미 선택된 더 큰 span에 포함되는가?
            boolean isContainedByExisting = result.stream()
                    .anyMatch(existing -> contains(existing, candidate));

            if (isContainedByExisting) {
                log.debug("[DEDUP] Skipping (contained): {} [{}~{}]",
                        candidate.text(), candidate.start(), candidate.end());
                continue;
            }

            // Step 2-2: Containment Check - 후보가 기존 선택된 것들을 포함하는가?
            boolean containsExisting = result.stream()
                    .anyMatch(existing -> contains(candidate, existing));

            if (containsExisting) {
                result.removeIf(existing -> {
                    if (contains(candidate, existing)) {
                        log.debug("[DEDUP] Replacing (containment): {} -> {}",
                                existing.text(), candidate.text());
                        return true;
                    }
                    return false;
                });
                result.add(candidate);
                continue;
            }

            // Step 3: Overlap Check - 겹치지만 포함 관계는 아닌 경우
            boolean overlaps = result.stream()
                    .anyMatch(existing -> overlaps(existing, candidate));

            if (overlaps) {
                log.debug("[DEDUP] Skipping (overlap): {} [{}~{}]",
                        candidate.text(), candidate.start(), candidate.end());
                continue;
            }

            result.add(candidate);
        }

        log.info("[DEDUP] Final selection: {} PII spans from {} candidates",
                result.size(), spans.size());

        return result;
    }

    private boolean contains(PiiSpan outer, PiiSpan inner) {
        return outer.start() <= inner.start() && outer.end() >= inner.end();
    }

    private boolean overlaps(PiiSpan a, PiiSpan b) {
        return a.start() < b.end() && b.start() < a.end();
    }

    // 동일한 이유로 @Observed 대신 manual API를 사용하여 'guardrail' 타입을 명시합니다.
    public String detokenize(String text) {
        return Observation.createNotStarted("pii.detokenize", observationRegistry)
                .highCardinalityKeyValue(ObservabilityConstants.LF_OBSERVATION_TYPE, ObservabilityConstants.LF_VAL_GUARDRAIL)
                .observe(() -> detokenizeInternal(text));
    }

    public String detokenizeInternal(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        PiiContext context = PiiContextHolder.getContext();
        String result = text;

        // Replace all tokens with original values
        for (var entry : context.getTokenToOriginal().entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }

        return result;
    }

    public Object detokenizeRec(Object input) {
        if (input instanceof String str) {
            return detokenizeInternal(str); // Use No-Span version for recursion
        }
        if (input instanceof Map<?, ?> map) {
            Map<Object, Object> newMap = new HashMap<>();
            map.forEach((k, v) -> newMap.put(k, detokenizeRec(v)));
            return newMap;
        }
        if (input instanceof List<?> list) {
            return list.stream().map(this::detokenizeRec).toList();
        }
        return input;
    }
}
