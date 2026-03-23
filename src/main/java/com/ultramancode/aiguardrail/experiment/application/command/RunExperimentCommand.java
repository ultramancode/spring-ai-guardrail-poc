package com.ultramancode.aiguardrail.experiment.application.command;

import com.ultramancode.aiguardrail.common.observability.domain.ScoreType;
import com.ultramancode.aiguardrail.experiment.domain.model.*;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RunExperimentCommand {

    private String datasetName;
    private String runName;
    private String modelLabel;
    private String vendor;
    private String model;

    @Builder.Default
    private ExperimentMode mode = ExperimentMode.FULL_WORKFLOW;

    private ExperimentTarget target;

    private String label;

    @Builder.Default
    private FieldMapping fieldMapping = FieldMapping.builder().build();

    @Builder.Default
    private EvaluationConfig evaluation = EvaluationConfig.builder().build();

    @Builder.Default
    private PromptConfig prompt = PromptConfig.builder().build();

    @Builder.Default
    private String scoreName = ScoreType.EXPERIMENT_SCORE.getValue();

    @Builder.Default
    private String targetGuardrail = ExperimentGuardrailMode.FULL.getValue();

    public void normalizeAndValidateOrThrow() {
        if (datasetName == null || datasetName.isBlank()) {
            throw new IllegalArgumentException("Dataset name must not be blank.");
        }
        datasetName = datasetName.trim();

        if (runName == null || runName.isBlank()) {
            runName = ExperimentDefaults.DEFAULT_RUN_NAME_PREFIX + System.currentTimeMillis();
        } else {
            runName = runName.trim();
        }
        if (mode == null) {
            throw new IllegalArgumentException("Experiment mode must not be null.");
        }
        if (mode != ExperimentMode.FULL_WORKFLOW && target == null) {
            throw new IllegalArgumentException("Experiment target must not be null.");
        }
        if (fieldMapping == null) {
            throw new IllegalArgumentException("Field mapping must not be null.");
        }
        fieldMapping.validateOrThrow();

        if (evaluation == null) {
            throw new IllegalArgumentException("Evaluation configuration must not be null.");
        }
        evaluation.normalizeAndValidateOrThrow();
    }

    @Data
    @Builder
    public static class FieldMapping {
        @Builder.Default
        private String input = ExperimentDefaults.DEFAULT_INPUT_FIELD;
        @Builder.Default
        private String expected = ExperimentDefaults.DEFAULT_EXPECTED_FIELD;
        private String expectedReason;

        public void validateOrThrow() {
            if (input == null || input.isBlank()) {
                throw new IllegalArgumentException("Field mapping input must not be blank.");
            }
            if (expected == null || expected.isBlank()) {
                throw new IllegalArgumentException("Field mapping expected must not be blank.");
            }
        }
    }

    @Data
    @Builder
    public static class EvaluationConfig {
        @Builder.Default
        private String type = ExperimentDefaults.DEFAULT_EVALUATION_TYPE;
        @Builder.Default
        private double threshold = ExperimentDefaults.DEFAULT_THRESHOLD;
        @Builder.Default
        private boolean evaluateReason = true;
        @Builder.Default
        private ExperimentComparisonMode comparisonMode = ExperimentComparisonMode.MASKED_ONLY;
        private EvaluationMethod resolvedMethod;

        public void normalizeAndValidateOrThrow() {
            if (comparisonMode == null) {
                throw new IllegalArgumentException("Evaluation comparison mode must not be null.");
            }
            resolvedMethod = EvaluationMethod.fromValue(type);
            type = resolvedMethod.getValue();

            if (Double.isNaN(threshold) || Double.isInfinite(threshold)) {
                throw new IllegalArgumentException("Evaluation threshold must be a finite number.");
            }
            if (threshold < 0.0 || threshold > 1.0) {
                throw new IllegalArgumentException("Evaluation threshold must be between 0.0 and 1.0.");
            }
        }

        public EvaluationMethod resolvedMethodOrThrow() {
            if (resolvedMethod == null) {
                throw new IllegalStateException("Evaluation configuration must be validated before use.");
            }
            return resolvedMethod;
        }
    }

    @Data
    @Builder
    public static class PromptConfig {
        private String name;
        private String version;
        private String systemPrompt;
    }
}
