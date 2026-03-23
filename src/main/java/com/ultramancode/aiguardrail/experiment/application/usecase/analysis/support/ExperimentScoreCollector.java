package com.ultramancode.aiguardrail.experiment.application.usecase.analysis.support;

import com.ultramancode.aiguardrail.common.util.PositiveConfigResolver;
import com.ultramancode.aiguardrail.common.util.StringValueUtils;
import com.ultramancode.aiguardrail.experiment.application.port.out.EvaluationRepositoryPort;
import com.ultramancode.aiguardrail.experiment.application.result.ScoreResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * runName에 매핑된 trace 집합 기준으로 점수를 수집한다.
 */
@Component
@RequiredArgsConstructor
public class ExperimentScoreCollector {

    private static final int DEFAULT_SCORE_PAGE_SIZE = 200;
    private static final int DEFAULT_MAX_SCORE_PAGES = 1000;

    private final EvaluationRepositoryPort evaluationPort;

    @Value("${experiment.analysis.score-page-size:" + DEFAULT_SCORE_PAGE_SIZE + "}")
    private int configuredScorePageSize;

    @Value("${experiment.analysis.max-score-pages:" + DEFAULT_MAX_SCORE_PAGES + "}")
    private int configuredMaxScorePages;

    /**
     * 점수명 매칭 시 사용하는 공통 정규화 규칙.
     */
    public static String normalizeScoreNameForMatch(String scoreName) {
        if (scoreName == null) {
            return "";
        }
        return scoreName.trim().toLowerCase(Locale.ROOT);
    }

    public List<ScoreResult> collectForRun(
            Set<String> traceIdsForRun,
            String humanScoreName,
            String autoScoreName
    ) {
        Set<String> targetScoreNames = buildTargetScoreNames(humanScoreName, autoScoreName);
        Set<String> normalizedTargetScoreNames = normalizeScoreNames(targetScoreNames);
        return collectByPagedScanOnly(traceIdsForRun, normalizedTargetScoreNames);
    }

    private List<ScoreResult> collectByPagedScanOnly(
            Set<String> traceIdsForRun,
            Set<String> normalizedScoreNames
    ) {
        int scorePageSize = resolveScorePageSize();
        int maxScorePages = resolveMaxScorePages();
        Map<String, ScoreResult> scoreBySelectionKey = new LinkedHashMap<>();
        int page = 1;

        while (true) {
            if (page > maxScorePages) {
                throw new IllegalStateException("Exceeded max score pages: " + maxScorePages);
            }

            EvaluationRepositoryPort.ScorePageResult pageResult = evaluationPort.fetchScores(page, scorePageSize);
            if (pageResult.rawCount() <= 0) {
                break;
            }

            List<ScoreResult> pageScores = pageResult.scores();
            for (ScoreResult score : pageScores) {
                if (!traceIdsForRun.contains(score.traceId())) {
                    continue;
                }
                if (!containsNormalizedScoreName(normalizedScoreNames, score.name())) {
                    continue;
                }

                mergeLatestScore(scoreBySelectionKey, score);
            }

            if (pageResult.rawCount() < scorePageSize) {
                break;
            }
            page++;
        }

        return List.copyOf(scoreBySelectionKey.values());
    }

    private int resolveScorePageSize() {
        return PositiveConfigResolver.resolve(
                configuredScorePageSize,
                DEFAULT_SCORE_PAGE_SIZE,
                "[ANALYSIS]",
                "experiment.analysis.score-page-size"
        );
    }

    private int resolveMaxScorePages() {
        return PositiveConfigResolver.resolve(
                configuredMaxScorePages,
                DEFAULT_MAX_SCORE_PAGES,
                "[ANALYSIS]",
                "experiment.analysis.max-score-pages"
        );
    }

    private Set<String> buildTargetScoreNames(String humanScoreName, String autoScoreName) {
        Set<String> targetScoreNames = new LinkedHashSet<>();
        targetScoreNames.add(humanScoreName.trim());
        targetScoreNames.add(autoScoreName.trim());
        return targetScoreNames;
    }

    private Set<String> normalizeScoreNames(Set<String> scoreNames) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String scoreName : scoreNames) {
            normalized.add(normalizeScoreNameForMatch(scoreName));
        }
        return normalized;
    }

    private boolean containsNormalizedScoreName(Set<String> normalizedNames, String candidate) {
        String normalizedCandidate = normalizeScoreNameForMatch(candidate);
        return normalizedNames.contains(normalizedCandidate);
    }

    private void mergeLatestScore(Map<String, ScoreResult> target, ScoreResult candidate) {
        String selectionKey = buildScoreSelectionKey(candidate);
        ScoreResult existing = target.get(selectionKey);
        if (existing == null || isCandidateNewer(candidate, existing)) {
            target.put(selectionKey, candidate);
        }
    }

    private String buildScoreSelectionKey(ScoreResult score) {
        return String.join(
                "|",
                String.valueOf(score.traceId()),
                normalizeScoreNameForMatch(score.name())
        );
    }

    private boolean isCandidateNewer(ScoreResult candidate, ScoreResult existing) {
        Long candidateTime = candidate.createdAtEpochMillis();
        Long existingTime = existing.createdAtEpochMillis();

        if (candidateTime == null && existingTime == null) {
            String candidateId = StringValueUtils.asNonBlankString(candidate.id());
            String existingId = StringValueUtils.asNonBlankString(existing.id());
            if (candidateId != null && existingId == null) {
                return true;
            }
            return false;
        }
        if (candidateTime == null) {
            return false;
        }
        if (existingTime == null) {
            return true;
        }
        return candidateTime > existingTime;
    }
}
