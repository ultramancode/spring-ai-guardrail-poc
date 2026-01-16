package com.ultramancode.aiguardrail.application.guardrail.pii;

import com.ultramancode.aiguardrail.infrastructure.guardrail.pii.PhileasScanner;
import com.ultramancode.aiguardrail.infrastructure.guardrail.pii.PresidioClient;
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
public class PiiService {

    private final PhileasScanner phileasScanner;
    private final PresidioClient presidioClient;

    public PiiService(PhileasScanner phileasScanner, PresidioClient presidioClient) {
        this.phileasScanner = phileasScanner;
        this.presidioClient = presidioClient;
    }

    public String tokenize(String text) {
        if (text == null || text.isBlank()) return text;

        List<PhileasScanner.PiiSpan> allSpans = new ArrayList<>();
        allSpans.addAll(phileasScanner.scan(text));
        allSpans.addAll(presidioClient.analyze(text));

        // ============================================================
        // DEDUPLICATION ALGORITHM
        // ============================================================
        // Strategy: Containment > Score > First Match
        // 
        // 1. Score 기반 정렬: 신뢰도가 높은 것이 우선
        // 2. Containment 처리: A가 B를 완전히 포함하면 A 선택 (부분 마스킹 방지)
        // 3. Overlap 필터링: 겹치는 후보는 스킵 (이중 마스킹 방지)
        // ============================================================
        List<PhileasScanner.PiiSpan> filteredSpans = advancedDeduplication(allSpans);

        // 3. Sort by START INDEX (descending) for safe string replacement
        // Reason: Replacing from the end prevents index shifting issues
        filteredSpans.sort(Comparator.comparingInt(PhileasScanner.PiiSpan::start).reversed());

        PiiContext context = PiiContextHolder.getContext();
        StringBuilder sb = new StringBuilder(text);

        for (PhileasScanner.PiiSpan span : filteredSpans) {
            String original = text.substring(span.start(), span.end());
            String token = context.getOrCreateToken(original, span.type());
            sb.replace(span.start(), span.end(), token);
        }

        return sb.toString();
    }

    /**
     * Deduplication algorithm for merging results from multiple PII detection engines.
     * 
     * <p><b>Strategy Overview:</b></p>
     * <ul>
     *   <li><b>Step 1 - Score Sort:</b> Higher confidence spans are processed first</li>
     *   <li><b>Step 2 - Containment:</b> If candidate fully contains an existing span, replace it (e.g., "서울대학교병원" > "서울대학교")</li>
     *   <li><b>Step 3 - Overlap Filter:</b> Skip candidates that overlap with already selected spans</li>
     * </ul>
     *
     * @param spans Raw detection results from all engines (Phileas + Presidio)
     * @return Deduplicated list of non-overlapping PII spans
     */
    private List<PhileasScanner.PiiSpan> advancedDeduplication(List<PhileasScanner.PiiSpan> spans) {
        // Step 1: Score 내림차순 정렬 (높은 신뢰도가 먼저 선택될 기회를 얻음)
        spans.sort(Comparator.comparingDouble(PhileasScanner.PiiSpan::score).reversed());
        
        List<PhileasScanner.PiiSpan> result = new ArrayList<>();
        
        for (PhileasScanner.PiiSpan candidate : spans) {
            // Step 2-1: Containment Check - 이미 선택된 더 큰 span에 포함되는가?
            boolean isContainedByExisting = result.stream()
                .anyMatch(existing -> contains(existing, candidate));
            
            if (isContainedByExisting) {
                // 이미 더 큰 span이 커버하고 있으므로 스킵
                log.debug("[DEDUP] Skipping (contained): {} [{}~{}]", 
                    candidate.text(), candidate.start(), candidate.end());
                continue;
            }
            
            // Step 2-2: Containment Check - 후보가 기존 선택된 것들을 포함하는가?
            boolean containsExisting = result.stream()
                .anyMatch(existing -> contains(candidate, existing));
            
            if (containsExisting) {
                // 후보가 더 크므로 기존 것들 중 포함되는 것들 제거 후 후보 추가
                // 예: "서울대학교병원"이 오면 "서울대학교"는 제거
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
                // 먼저 선택된 것(Score가 높았던 것)이 우선이므로 스킵
                log.debug("[DEDUP] Skipping (overlap): {} [{}~{}]", 
                    candidate.text(), candidate.start(), candidate.end());
                continue;
            }
            
            // 모든 검증 통과 - 결과에 추가
            result.add(candidate);
        }
        
        log.info("[DEDUP] Final selection: {} PII spans from {} candidates", 
            result.size(), spans.size());
        
        return result;
    }

    /**
     * Checks if 'outer' span completely contains 'inner' span.
     * Used for Containment-based deduplication (e.g., "서울대학교병원" contains "서울대학교")
     */
    private boolean contains(PhileasScanner.PiiSpan outer, PhileasScanner.PiiSpan inner) {
        return outer.start() <= inner.start() && outer.end() >= inner.end();
    }

    /**
     * Checks if two spans overlap (but neither fully contains the other).
     * Used to prevent double-masking of partially overlapping detections.
     */
    private boolean overlaps(PhileasScanner.PiiSpan a, PhileasScanner.PiiSpan b) {
        return a.start() < b.end() && b.start() < a.end();
    }

    public String detokenize(String text) {
        if (text == null || text.isBlank()) return text;

        PiiContext context = PiiContextHolder.getContext();
        String result = text;
        
        // Replace all tokens with original values
        for (var entry : context.getTokenToOriginal().entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        
        return result;
    }

    /**
     * Recursively detokenizes objects (Maps, Lists, Strings).
     */
    public Object detokenizeRec(Object input) {
        if (input instanceof String str) {
            return detokenize(str);
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
