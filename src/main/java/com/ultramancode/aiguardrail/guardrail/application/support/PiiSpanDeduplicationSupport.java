package com.ultramancode.aiguardrail.guardrail.application.support;

import com.ultramancode.aiguardrail.guardrail.domain.PiiSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PII 스팬의 중복/포함/겹침 정리를 담당합니다.
 */
@Slf4j
@Component
public class PiiSpanDeduplicationSupport {

    public List<PiiSpan> deduplicate(List<PiiSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return List.of();
        }

        List<PiiSpan> sortedSpans = new ArrayList<>(spans);
        sortedSpans.sort(Comparator.comparingDouble(PiiSpan::score).reversed());

        List<PiiSpan> result = new ArrayList<>();
        for (PiiSpan candidate : sortedSpans) {
            ContainmentDecision containmentDecision = handleContainment(result, candidate);
            if (containmentDecision.skipCandidate()) {
                continue;
            }

            if (hasOverlap(containmentDecision.baseSpans(), candidate)) {
                continue;
            }

            if (containmentDecision.replacedByContainment()) {
                result.clear();
                result.addAll(containmentDecision.baseSpans());
            }
            result.add(candidate);
        }

        log.info("[DEDUP] Final selection: {} PII spans from {} candidates", result.size(), spans.size());
        return result;
    }

    private ContainmentDecision handleContainment(List<PiiSpan> result, PiiSpan candidate) {
        boolean isContainedByExisting = result.stream()
                .anyMatch(existing -> contains(existing, candidate));
        if (isContainedByExisting) {
            log.debug(
                    "[DEDUP] Skipping (contained): type={} [{}~{}]",
                    candidate.type(),
                    candidate.start(),
                    candidate.end()
            );
            return ContainmentDecision.skip(result);
        }

        List<PiiSpan> baseSpans = new ArrayList<>();
        boolean replacedByContainment = false;
        for (PiiSpan existing : result) {
            if (contains(candidate, existing)) {
                log.debug(
                        "[DEDUP] Replacing (containment): type={} [{}~{}] -> type={} [{}~{}]",
                        existing.type(),
                        existing.start(),
                        existing.end(),
                        candidate.type(),
                        candidate.start(),
                        candidate.end()
                );
                replacedByContainment = true;
                continue;
            }

            baseSpans.add(existing);
        }

        if (replacedByContainment) {
            return ContainmentDecision.replace(baseSpans);
        }

        return ContainmentDecision.keep(result);
    }

    private boolean hasOverlap(List<PiiSpan> result, PiiSpan candidate) {
        boolean overlaps = result.stream()
                .anyMatch(existing -> overlaps(existing, candidate));
        if (overlaps) {
            log.debug(
                    "[DEDUP] Skipping (overlap): type={} [{}~{}]",
                    candidate.type(),
                    candidate.start(),
                    candidate.end()
            );
            return true;
        }

        return false;
    }

    private boolean contains(PiiSpan outer, PiiSpan inner) {
        return outer.start() <= inner.start() && outer.end() >= inner.end();
    }

    private boolean overlaps(PiiSpan left, PiiSpan right) {
        return left.start() < right.end() && right.start() < left.end();
    }

    private record ContainmentDecision(
            List<PiiSpan> baseSpans,
            boolean skipCandidate,
            boolean replacedByContainment
    ) {
        private static ContainmentDecision skip(List<PiiSpan> baseSpans) {
            return new ContainmentDecision(baseSpans, true, false);
        }

        private static ContainmentDecision replace(List<PiiSpan> baseSpans) {
            return new ContainmentDecision(baseSpans, false, true);
        }

        private static ContainmentDecision keep(List<PiiSpan> baseSpans) {
            return new ContainmentDecision(baseSpans, false, false);
        }
    }
}
