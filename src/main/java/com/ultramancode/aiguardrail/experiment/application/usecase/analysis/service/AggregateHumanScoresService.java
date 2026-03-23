package com.ultramancode.aiguardrail.experiment.application.usecase.analysis.service;

import com.ultramancode.aiguardrail.common.util.StringValueUtils;
import com.ultramancode.aiguardrail.experiment.application.command.AggregateHumanScoresCommand;
import com.ultramancode.aiguardrail.experiment.application.port.in.AggregateHumanScoresUseCase;
import com.ultramancode.aiguardrail.experiment.application.port.out.EvaluationRepositoryPort;
import com.ultramancode.aiguardrail.experiment.application.result.HumanEvaluationResult;
import com.ultramancode.aiguardrail.experiment.application.result.ScoreResult;
import com.ultramancode.aiguardrail.experiment.application.usecase.analysis.support.ExperimentScoreCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 실험 점수를 수집하고 사람 점수/자동 점수 합의율을 집계한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AggregateHumanScoresService implements AggregateHumanScoresUseCase {

    private static final double PASS_THRESHOLD = 0.5;
    private static final String SCORE_CATEGORY_HUMAN = "human";
    private static final String SCORE_CATEGORY_AUTO = "auto";

    private final EvaluationRepositoryPort evaluationPort;
    private final ExperimentScoreCollector scoreCollector;

    /**
     * 사람 점수와 자동 점수를 비교해 합의율을 계산한다.
     */
    @Override
    public HumanEvaluationResult aggregateHumanScores(AggregateHumanScoresCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Aggregate human scores command must not be null.");
        }
        command.normalizeAndValidateOrThrow();

        String runName = command.getRunName();
        String humanScoreName = command.getHumanScoreName();
        String autoScoreName = command.getAutoScoreName();

        long startTime = System.currentTimeMillis();
        log.info(
                "[ANALYSIS] Aggregating scores for run: {}, human: {}, auto: {}",
                runName,
                humanScoreName,
                autoScoreName
        );

        Set<String> traceIdsForRun = evaluationPort.fetchTraceIdsByRunName(runName);

        if (traceIdsForRun.isEmpty()) {
            log.warn("[ANALYSIS] No traceIds found for run: {}. Returning empty aggregation.", runName);
            AnalysisContext emptyContext = new AnalysisContext();
            return buildResult(
                    runName,
                    humanScoreName,
                    autoScoreName,
                    emptyContext,
                    startTime
            );
        }

        List<ScoreResult> scores = scoreCollector.collectForRun(traceIdsForRun, humanScoreName, autoScoreName);

        // 1) traceId 기준으로 사람 점수/자동 점수 최신값을 분류한다.
        Map<String, Map<String, Double>> categorized = categorizeScores(
                scores,
                humanScoreName,
                autoScoreName
        );
        Map<String, Double> humanMap = categorized.get(SCORE_CATEGORY_HUMAN);
        Map<String, Double> autoMap = categorized.get(SCORE_CATEGORY_AUTO);

        // 2) 분류 결과를 바탕으로 합의/불일치 통계를 계산한다.
        AnalysisContext context = performComparison(humanMap, autoMap);

        return buildResult(
                runName,
                humanScoreName,
                autoScoreName,
                context,
                startTime
        );
    }

    private HumanEvaluationResult buildResult(
            String runName,
            String humanScoreName,
            String autoScoreName,
            AnalysisContext context,
            long startTime
    ) {
        double agreementRate = context.comparedTotal > 0
                ? (double) context.agreementCount / context.comparedTotal
                : 0.0;

        return HumanEvaluationResult.builder()
                .runName(runName)
                .humanScoreName(humanScoreName)
                .autoScoreName(autoScoreName)
                .humanTotalCount(context.humanTotal)
                .humanPositiveCount(context.humanPositiveCount)
                .humanNegativeCount(context.humanNegativeCount)
                .autoComparedCount(context.comparedTotal)
                .autoPositiveCount(context.autoPositiveCount)
                .autoNegativeCount(context.autoNegativeCount)
                .agreementRate(agreementRate)
                .disagreementCount(context.disagreements.size())
                .disagreements(context.disagreements)
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    private Map<String, Map<String, Double>> categorizeScores(
            List<ScoreResult> scores,
            String humanScoreName,
            String autoScoreName
    ) {
        Map<String, Double> humanMap = new HashMap<>();
        Map<String, Double> autoMap = new HashMap<>();

        for (ScoreResult score : scores) {
            String traceId = score.traceId();
            if (scoreNameEquals(humanScoreName, score.name())) {
                humanMap.put(traceId, score.value());
            }
            if (scoreNameEquals(autoScoreName, score.name())) {
                autoMap.put(traceId, score.value());
            }
        }

        return Map.of(SCORE_CATEGORY_HUMAN, humanMap, SCORE_CATEGORY_AUTO, autoMap);
    }

    /**
     * 사람 점수 맵과 자동 점수 맵을 순회하며 합의/불일치 통계를 계산한다.
     */
    private AnalysisContext performComparison(Map<String, Double> humanMap, Map<String, Double> autoMap) {
        AnalysisContext analysisContext = new AnalysisContext();

        for (Map.Entry<String, Double> humanEntry : humanMap.entrySet()) {
            String traceId = humanEntry.getKey();
            double humanScore = humanEntry.getValue();

            analysisContext.humanTotal++;
            if (humanScore >= PASS_THRESHOLD) {
                analysisContext.humanPositiveCount++;
            } else {
                analysisContext.humanNegativeCount++;
            }

            if (autoMap.containsKey(traceId)) {
                double autoScore = autoMap.get(traceId);
                analysisContext.comparedTotal++;
                if (autoScore >= PASS_THRESHOLD) {
                    analysisContext.autoPositiveCount++;
                } else {
                    analysisContext.autoNegativeCount++;
                }

                if ((humanScore >= PASS_THRESHOLD) == (autoScore >= PASS_THRESHOLD)) {
                    analysisContext.agreementCount++;
                } else {
                    analysisContext.disagreements.put(
                            traceId,
                            String.format("Human:%b, Auto:%b", humanScore >= PASS_THRESHOLD, autoScore >= PASS_THRESHOLD)
                    );
                }
            }
        }
        return analysisContext;
    }

    private boolean scoreNameEquals(String left, String right) {
        if (StringValueUtils.asNonBlankString(left) == null) {
            return false;
        }
        if (StringValueUtils.asNonBlankString(right) == null) {
            return false;
        }
        return ExperimentScoreCollector.normalizeScoreNameForMatch(left)
                .equals(ExperimentScoreCollector.normalizeScoreNameForMatch(right));
    }

    /**
     * 집계 중간 계산을 담는 내부 상태 객체.
     */
    private static class AnalysisContext {
        int humanTotal = 0;
        int humanPositiveCount = 0;
        int humanNegativeCount = 0;
        int autoPositiveCount = 0;
        int autoNegativeCount = 0;
        int comparedTotal = 0;
        int agreementCount = 0;
        Map<String, String> disagreements = new HashMap<>();
    }

}
