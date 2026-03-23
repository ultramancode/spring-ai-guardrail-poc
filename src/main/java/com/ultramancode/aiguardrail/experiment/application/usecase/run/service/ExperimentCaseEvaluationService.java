package com.ultramancode.aiguardrail.experiment.application.usecase.run.service;

import com.ultramancode.aiguardrail.common.observability.command.RecordScoreCommand;
import com.ultramancode.aiguardrail.common.util.ErrorMessageResolver;
import com.ultramancode.aiguardrail.experiment.application.command.EvaluationCommand;
import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.application.port.out.EvaluationRepositoryPort;
import com.ultramancode.aiguardrail.experiment.application.result.CaseEvaluationOutcome;
import com.ultramancode.aiguardrail.experiment.application.result.EvaluationMatchResult;
import com.ultramancode.aiguardrail.experiment.application.result.SimilarityScore;
import com.ultramancode.aiguardrail.experiment.domain.model.EvaluationMethod;
import com.ultramancode.aiguardrail.experiment.domain.model.ExperimentComparisonMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentCaseEvaluationService {

    // Langfuse score comment의 comparison 필드에 기록되는 관측용 라벨입니다.
    // 평가 분기의 기준은 comparisonMode/실제 응답 상태이며, 아래 문자열은 기록 가독성 용도입니다.
    private static final String COMPARISON_MASKED_ONLY = "masked_only";
    private static final String COMPARISON_DETOKENIZED_ONLY = "detokenized_only";
    private static final String COMPARISON_FALLBACK_MASKED_ONLY = "detokenized_fallback(masked_only)";
    private static final String COMPARISON_FALLBACK_MASKED_THEN_DETOKENIZED = "detokenized_fallback(masked_then_detokenized)";
    private static final String COMPARISON_MASKED_THEN_DETOKENIZED_MASKED = "masked_then_detokenized(masked)";
    private static final String COMPARISON_MASKED_THEN_DETOKENIZED_DETOKENIZED = "masked_then_detokenized(detokenized)";

    private final ExperimentEvaluator evaluator;
    private final EvaluationRepositoryPort evaluationPort;

    /**
     * 비교 모드 설정에 따라 마스크/디토큰 응답 중 비교 대상을 선택합니다.
     */
    public CaseEvaluationOutcome performEvaluation(
            RunExperimentCommand command,
            String expected,
            String actual,
            String masked,
            boolean maskedFallbackUsed
    ) {
        EvaluationMethod method = command.getEvaluation().resolvedMethodOrThrow();
        double threshold = command.getEvaluation().getThreshold();
        ExperimentComparisonMode comparisonMode = command.getEvaluation().getComparisonMode();

        if (comparisonMode == ExperimentComparisonMode.MASKED_ONLY) {
            String comparedValueLabel = COMPARISON_MASKED_ONLY;
            if (maskedFallbackUsed) {
                comparedValueLabel = COMPARISON_FALLBACK_MASKED_ONLY;
            }
            EvaluationMatchResult result = evaluator.evaluateMatch(
                    buildEvaluationCommand(expected, masked, method, threshold)
            );
            return new CaseEvaluationOutcome(result, masked, comparedValueLabel);
        }
        if (comparisonMode == ExperimentComparisonMode.DETOKENIZED_ONLY) {
            EvaluationMatchResult result = evaluator.evaluateMatch(
                    buildEvaluationCommand(expected, actual, method, threshold)
            );
            return new CaseEvaluationOutcome(result, actual, COMPARISON_DETOKENIZED_ONLY);
        }
        if (maskedFallbackUsed) {
            EvaluationMatchResult result = evaluator.evaluateMatch(
                    buildEvaluationCommand(expected, actual, method, threshold)
            );
            return new CaseEvaluationOutcome(result, actual, COMPARISON_FALLBACK_MASKED_THEN_DETOKENIZED);
        }

        EvaluationMatchResult maskedResult = evaluator.evaluateMatch(
                buildEvaluationCommand(expected, masked, method, threshold)
        );
        if (maskedResult.match()) {
            return new CaseEvaluationOutcome(maskedResult, masked, COMPARISON_MASKED_THEN_DETOKENIZED_MASKED);
        }

        EvaluationMatchResult detokenizedResult = evaluator.evaluateMatch(
                buildEvaluationCommand(expected, actual, method, threshold)
        );
        return new CaseEvaluationOutcome(detokenizedResult, actual, COMPARISON_MASKED_THEN_DETOKENIZED_DETOKENIZED);
    }

    public Double evaluateReasonScore(
            RunExperimentCommand command,
            String itemId,
            String expectedReason,
            String actualResponse
    ) {
        if (!command.getEvaluation().isEvaluateReason()) {
            return null;
        }
        if (expectedReason == null || expectedReason.isBlank()) {
            return null;
        }

        try {
            SimilarityScore similarity = evaluator.evaluateWithLlmJudge(expectedReason, actualResponse);
            if (similarity == null) {
                return null;
            }
            return similarity.score();
        } catch (RuntimeException e) {
            String resolvedErrorMessage = ErrorMessageResolver.resolve(e, "RuntimeException");
            log.warn(
                    "[EXPERIMENT] Reason score evaluation failed. runName={}, itemId={}, cause={}",
                    command.getRunName(),
                    itemId,
                    resolvedErrorMessage,
                    e
            );
            return null;
        }
    }

    public void recordResults(
            RunExperimentCommand command,
            String itemId,
            String traceId,
            String observationId,
            double score,
            String reason,
            String comparedValueLabel
    ) {
        evaluationPort.recordScore(RecordScoreCommand.builder()
                .traceId(traceId)
                .observationId(observationId)
                .scoreName(command.getScoreName())
                .value(score)
                .comment(String.format(
                        "evalType=%s, comparison=%s, runMode=%s | %s",
                        command.getEvaluation().getType(),
                        comparedValueLabel,
                        command.getMode().getValue(),
                        reason
                ))
                .build());

        evaluationPort.linkDatasetRunItem(command.getRunName(), itemId, traceId, observationId);
    }

    private EvaluationCommand buildEvaluationCommand(
            String expected,
            String actual,
            EvaluationMethod method,
            double threshold
    ) {
        return EvaluationCommand.builder()
                .expected(expected)
                .actual(actual)
                .method(method)
                .threshold(threshold)
                .build();
    }

}
