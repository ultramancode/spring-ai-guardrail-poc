package com.ultramancode.aiguardrail.experiment.presentation.mapper;

import com.ultramancode.aiguardrail.common.llm.ModelNameResolver;
import com.ultramancode.aiguardrail.experiment.application.command.AggregateHumanScoresCommand;
import com.ultramancode.aiguardrail.experiment.application.command.ExperimentCommandParsers;
import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.presentation.request.ExperimentRequest;

/**
 * 실험 관련 매핑 유틸리티
 */
public class ExperimentMapper {

    private ExperimentMapper() {
    }

    public static RunExperimentCommand toCommand(ExperimentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Experiment request must not be null.");
        }

        String normalizedVendor = normalizeToNull(request.getVendor());
        String normalizedModel = normalizeToNull(request.getModel());
        String modelLabel = resolveModelLabel(request, normalizedVendor, normalizedModel);

        ExperimentRequest.FieldMapping fieldMappingReq = defaultIfNull(
                request.getFieldMapping(),
                ExperimentRequest.FieldMapping.builder().build()
        );
        ExperimentRequest.EvaluationConfig evalReq = defaultIfNull(
                request.getEvaluation(),
                ExperimentRequest.EvaluationConfig.builder().build()
        );
        ExperimentRequest.PromptConfig promptReq = defaultIfNull(
                request.getPrompt(),
                ExperimentRequest.PromptConfig.builder().build()
        );

        RunExperimentCommand.FieldMapping.FieldMappingBuilder fieldMappingBuilder =
                RunExperimentCommand.FieldMapping.builder();
        String inputField = normalizeToNull(fieldMappingReq.getInput());
        if (inputField != null) {
            fieldMappingBuilder.input(inputField);
        }
        String expectedField = normalizeToNull(fieldMappingReq.getExpected());
        if (expectedField != null) {
            fieldMappingBuilder.expected(expectedField);
        }
        String expectedReasonField = normalizeToNull(fieldMappingReq.getExpectedReason());
        if (expectedReasonField != null) {
            fieldMappingBuilder.expectedReason(expectedReasonField);
        }

        RunExperimentCommand.EvaluationConfig.EvaluationConfigBuilder evaluationBuilder =
                RunExperimentCommand.EvaluationConfig.builder();
        String evaluationType = normalizeToNull(evalReq.getType());
        if (evaluationType != null) {
            evaluationBuilder.type(evaluationType);
        }
        evaluationBuilder.threshold(evalReq.getThreshold());
        evaluationBuilder.evaluateReason(evalReq.isEvaluateReason());
        String comparisonModeValue = normalizeToNull(evalReq.getComparisonMode());
        if (comparisonModeValue != null) {
            evaluationBuilder.comparisonMode(ExperimentCommandParsers.parseComparisonMode(comparisonModeValue));
        }

        RunExperimentCommand.RunExperimentCommandBuilder commandBuilder = RunExperimentCommand.builder()
                .datasetName(request.getDatasetName())
                .runName(request.getRunName())
                .modelLabel(modelLabel)
                .vendor(normalizedVendor)
                .model(normalizedModel)
                .label(request.getLabel())

                .fieldMapping(fieldMappingBuilder.build())

                .evaluation(evaluationBuilder.build())

                .prompt(RunExperimentCommand.PromptConfig.builder()
                        .name(normalizeToNull(promptReq.getName()))
                        .version(normalizeToNull(promptReq.getVersion()))
                        .systemPrompt(promptReq.getSystemPrompt())
                        .build());

        String mode = normalizeToNull(request.getMode());
        if (mode != null) {
            commandBuilder.mode(ExperimentCommandParsers.parseMode(mode));
        }

        String targetGuardrail = normalizeToNull(request.getTargetGuardrail());
        if (targetGuardrail != null) {
            commandBuilder.targetGuardrail(ExperimentCommandParsers.parseTargetGuardrail(targetGuardrail));
        }

        String target = normalizeToNull(request.getTarget());
        if (target != null) {
            commandBuilder.target(ExperimentCommandParsers.parseTarget(target));
        }

        String scoreName = normalizeToNull(request.getScoreName());
        if (scoreName != null) {
            commandBuilder.scoreName(scoreName);
        }

        return commandBuilder.build();
    }

    public static AggregateHumanScoresCommand toAggregateHumanScoresCommand(
            String runName,
            String humanScoreName,
            String autoScoreName,
            String legacyLlmScoreName
    ) {
        String normalizedAutoScoreName = normalizeToNull(autoScoreName);
        String normalizedLegacyScoreName = normalizeToNull(legacyLlmScoreName);

        String resolvedAutoScoreName = normalizedLegacyScoreName;
        if (normalizedAutoScoreName != null) {
            if (normalizedLegacyScoreName != null) {
                if (!normalizedAutoScoreName.equalsIgnoreCase(normalizedLegacyScoreName)) {
                    throw new IllegalArgumentException(
                            "autoScoreName and llmScoreName(alias) must match when both are provided."
                    );
                }
            }
            resolvedAutoScoreName = normalizedAutoScoreName;
        }

        return AggregateHumanScoresCommand.builder()
                .runName(runName)
                .humanScoreName(humanScoreName)
                .autoScoreName(resolvedAutoScoreName)
                .build();
    }

    private static String resolveModelLabel(ExperimentRequest request, String vendor, String model) {
        String modelLabel = request.getModelLabel();
        if (modelLabel != null) {
            if (!modelLabel.isBlank()) {
                return modelLabel.trim();
            }
        }

        return ModelNameResolver.resolve(vendor, model);
    }

    private static <T> T defaultIfNull(T value, T defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    private static String normalizeToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
