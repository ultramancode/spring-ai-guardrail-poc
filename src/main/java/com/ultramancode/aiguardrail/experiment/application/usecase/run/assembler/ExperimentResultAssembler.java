package com.ultramancode.aiguardrail.experiment.application.usecase.run.assembler;

import com.ultramancode.aiguardrail.common.observability.ObservabilityTags;
import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.application.result.CaseEvaluationOutcome;
import com.ultramancode.aiguardrail.experiment.application.result.ExperimentResult;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.support.ExperimentPromptSupport;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.support.dataset.DatasetItemIdResolver;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.support.dataset.ExperimentFieldMapper;
import io.micrometer.observation.Observation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExperimentResultAssembler {

    private final ExperimentPromptSupport promptSupport;
    private final ExperimentFieldMapper fieldMapper;
    private final DatasetItemIdResolver datasetItemIdResolver;

    public ExperimentResult buildPromptResolutionFailureResult(
            RunExperimentCommand command,
            long startTime,
            int totalItemCount,
            List<Map<String, Object>> items,
            String errorMessage,
            Observation rootObs
    ) {
        List<ExperimentResult.TestCaseResult> details = new ArrayList<>();
        appendPromptResolutionFailureDetails(command, items, 0, errorMessage, details);
        boolean detailsSampled = details.size() < totalItemCount;
        String detailCoverage = resolveDetailCoverage(detailsSampled, true);
        Integer sampledDetailCount = resolveSampledDetailCount(details, detailsSampled, true);

        long duration = System.currentTimeMillis() - startTime;

        if (rootObs != null) {
            rootObs.highCardinalityKeyValue(
                    ObservabilityTags.KEY_OUTPUT,
                    String.format(
                            "PromptResolutionFailure: 0/%d (0.0%%) | details=%d",
                            totalItemCount,
                            details.size()
                    )
            );
        }

        return ExperimentResult.builder()
                .runName(command.getRunName())
                .datasetName(command.getDatasetName())
                .modelLabel(command.getModelLabel())
                .promptInfo(promptSupport.buildPromptInfo(command))
                .total(totalItemCount)
                .passed(0)
                .failed(totalItemCount)
                .evaluatedCount(0)
                .errorCount(totalItemCount)
                .recordingErrorCount(0)
                .accuracy(0.0)
                .f1Score(0.0)
                .confusionMatrix(null)
                .averageReasonScore(null)
                .executionTimeMs(duration)
                .partialResult(true)
                .sampledDetailCount(sampledDetailCount)
                .detailCoverage(detailCoverage)
                .details(details)
                .build();
    }

    public void appendPromptResolutionFailureDetails(
            RunExperimentCommand command,
            List<Map<String, Object>> items,
            int startIndex,
            String errorMessage,
            List<ExperimentResult.TestCaseResult> details
    ) {
        if (items == null || items.isEmpty()) {
            return;
        }

        String resolvedErrorMessage = errorMessage;
        if (resolvedErrorMessage == null || resolvedErrorMessage.isBlank()) {
            resolvedErrorMessage = "Unknown prompt resolution error";
        }

        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            int itemIndex = startIndex + i;
            String itemId = resolveItemId(item, itemIndex);
            String input = resolveFieldValue(item, resolveInputKey(command));
            String expected = resolveFieldValue(item, resolveExpectedKey(command));
            String expectedReason = resolveFieldValue(item, resolveExpectedReasonKey(command));

            details.add(buildFailedResult(
                    input,
                    expected,
                    expectedReason,
                    String.format("System prompt resolution failed (itemId=%s): %s", itemId, resolvedErrorMessage)
            ));
        }
    }

    private String resolveInputKey(RunExperimentCommand command) {
        if (command == null || command.getFieldMapping() == null) {
            return null;
        }
        return command.getFieldMapping().getInput();
    }

    private String resolveExpectedKey(RunExperimentCommand command) {
        if (command == null || command.getFieldMapping() == null) {
            return null;
        }
        return command.getFieldMapping().getExpected();
    }

    private String resolveExpectedReasonKey(RunExperimentCommand command) {
        if (command == null || command.getFieldMapping() == null) {
            return null;
        }
        return command.getFieldMapping().getExpectedReason();
    }

    private String resolveItemId(Map<String, Object> item, int itemIndex) {
        return datasetItemIdResolver.resolveOrDefault(item, datasetItemIdResolver.defaultItemId(itemIndex));
    }

    private String resolveFieldValue(Map<String, Object> source, String fieldPath) {
        String resolved = fieldMapper.extractField(source, fieldPath);
        if (resolved == null) {
            return "";
        }
        return resolved;
    }

    public ExperimentResult.TestCaseResult buildSucceededResult(
            String inputQuestion,
            String expectedOutput,
            String expectedReason,
            String actualResponse,
            CaseEvaluationOutcome evaluationOutcome,
            Double reasonScore,
            String traceId,
            String observationId,
            String recordingError
    ) {
        return ExperimentResult.TestCaseResult.builder()
                .input(inputQuestion)
                .expected(expectedOutput)
                .actual(actualResponse)
                .evaluationActual(evaluationOutcome.comparedActual())
                .match(evaluationOutcome.matchResult().match())
                .score(evaluationOutcome.matchResult().score())
                .traceId(traceId)
                .observationId(observationId)
                .recordingError(recordingError)
                .expectedReason(expectedReason)
                .evaluationReason(evaluationOutcome.matchResult().reason())
                .reasonScore(reasonScore)
                .build();
    }

    public ExperimentResult.TestCaseResult buildFailedResult(
            String inputQuestion,
            String expectedOutput,
            String expectedReason,
            String errorMessage
    ) {
        return ExperimentResult.TestCaseResult.builder()
                .input(inputQuestion)
                .expected(expectedOutput)
                .actual("")
                .evaluationActual("")
                .match(false)
                .score(0.0)
                .errorMessage(errorMessage)
                .expectedReason(expectedReason)
                .build();
    }

    public ExperimentResult buildFinalResult(
            RunExperimentCommand command,
            long startTime,
            int total,
            int passed,
            int errorCount,
            int recordingErrorCount,
            int truePositive,
            int trueNegative,
            int falsePositive,
            int falseNegative,
            Double averageReasonScore,
            boolean detailsSampled,
            List<ExperimentResult.TestCaseResult> details,
            boolean partialResult,
            Observation rootObs
    ) {
        double accuracy = (total > 0) ? (double) passed / total : 0.0;
        ExperimentResult.ConfusionMatrix confusionMatrix = buildConfusionMatrix(
                truePositive,
                trueNegative,
                falsePositive,
                falseNegative
        );
        double f1Score = computeF1Score(confusionMatrix);
        int evaluatedCount = total - errorCount;
        long duration = System.currentTimeMillis() - startTime;
        String detailCoverage = resolveDetailCoverage(detailsSampled, partialResult);
        Integer sampledDetailCount = resolveSampledDetailCount(details, detailsSampled, partialResult);

        if (rootObs != null) {
            rootObs.highCardinalityKeyValue(
                    ObservabilityTags.KEY_OUTPUT,
                    String.format(
                            "Passed: %d/%d (%.1f%%) | F1: %.2f | RecordingWarn: %d",
                            passed,
                            total,
                            accuracy * 100,
                            f1Score,
                            recordingErrorCount
                    )
            );
        }

        return ExperimentResult.builder()
                .runName(command.getRunName())
                .datasetName(command.getDatasetName())
                .modelLabel(command.getModelLabel())
                .promptInfo(promptSupport.buildPromptInfo(command))
                .total(total)
                .passed(passed)
                .failed(total - passed)
                .evaluatedCount(evaluatedCount)
                .errorCount(errorCount)
                .recordingErrorCount(recordingErrorCount)
                .accuracy(accuracy)
                .f1Score(f1Score)
                .confusionMatrix(confusionMatrix)
                .averageReasonScore(averageReasonScore)
                .executionTimeMs(duration)
                .partialResult(partialResult)
                .sampledDetailCount(sampledDetailCount)
                .detailCoverage(detailCoverage)
                .details(details)
                .build();
    }

    private String resolveDetailCoverage(boolean detailsSampled, boolean partialResult) {
        if (partialResult || detailsSampled) {
            return "SAMPLED";
        }
        return "FULL";
    }

    private Integer resolveSampledDetailCount(
            List<ExperimentResult.TestCaseResult> details,
            boolean detailsSampled,
            boolean partialResult
    ) {
        if (!(detailsSampled || partialResult)) {
            return null;
        }

        if (details == null) {
            return 0;
        }

        return details.size();
    }

    private ExperimentResult.ConfusionMatrix buildConfusionMatrix(
            int truePositive,
            int trueNegative,
            int falsePositive,
            int falseNegative
    ) {
        double precision = (truePositive + falsePositive) > 0
                ? (double) truePositive / (truePositive + falsePositive)
                : 0.0;
        double recall = (truePositive + falseNegative) > 0
                ? (double) truePositive / (truePositive + falseNegative)
                : 0.0;

        return ExperimentResult.ConfusionMatrix.builder()
                .truePositive(truePositive)
                .trueNegative(trueNegative)
                .falsePositive(falsePositive)
                .falseNegative(falseNegative)
                .precision(precision)
                .recall(recall)
                .build();
    }

    private double computeF1Score(ExperimentResult.ConfusionMatrix confusionMatrix) {
        double denominator = confusionMatrix.getPrecision() + confusionMatrix.getRecall();
        if (denominator == 0.0) {
            return 0.0;
        }
        return 2 * (confusionMatrix.getPrecision() * confusionMatrix.getRecall()) / denominator;
    }
}
