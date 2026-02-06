package com.ultramancode.aiguardrail.experiment.presentation.mapper;

import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.domain.ExperimentMode;
import com.ultramancode.aiguardrail.experiment.domain.ExperimentTarget;
import com.ultramancode.aiguardrail.experiment.presentation.request.ExperimentRequest;

/**
 * 실험 관련 매핑 유틸리티
 */
public class ExperimentMapper {

    private static final String DEFAULT_RUN_NAME_PREFIX = "experiment-";
    private static final String DEFAULT_SCORE_NAME = "experiment-score";
    private static final String DEFAULT_INPUT_FIELD = "input";
    private static final String DEFAULT_EXPECTED_FIELD = "verdict";

    public static RunExperimentCommand toCommand(ExperimentRequest request) {
        // Null Safety Utils
        var fieldMappingReq = request.getFieldMapping() != null ? request.getFieldMapping()
                : ExperimentRequest.FieldMapping.builder().build();
        var evalReq = request.getEvaluation() != null ? request.getEvaluation()
                : ExperimentRequest.EvaluationConfig.builder().build();
        var promptReq = request.getPrompt() != null ? request.getPrompt()
                : ExperimentRequest.PromptConfig.builder().build();

        // Mapping logic
        return RunExperimentCommand.builder()
                .datasetName(request.getDatasetName())
                .runName(request.getRunName() != null ? request.getRunName()
                        : DEFAULT_RUN_NAME_PREFIX + System.currentTimeMillis())
                .modelLabel(request.getModelLabel())
                .mode(request.getMode() != null ? ExperimentMode.valueOf(request.getMode()) : ExperimentMode.PROMPT_ONLY)
                .target(request.getTarget() != null ? ExperimentTarget.valueOf(request.getTarget()) : null)
                .label(request.getLabel())
                .scoreName(request.getScoreName() != null ? request.getScoreName() : DEFAULT_SCORE_NAME)

                .fieldMapping(RunExperimentCommand.FieldMapping.builder()
                        .input(fieldMappingReq.getInput() != null ? fieldMappingReq.getInput() : DEFAULT_INPUT_FIELD)
                        .expected(fieldMappingReq.getExpected() != null ? fieldMappingReq.getExpected() : DEFAULT_EXPECTED_FIELD)
                        .expectedReason(fieldMappingReq.getExpectedReason())
                        .build())

                .evaluation(RunExperimentCommand.EvaluationConfig.builder()
                        .type(evalReq.getType() != null ? evalReq.getType() : com.ultramancode.aiguardrail.experiment.domain.EvaluationMethod.EXACT_MATCH.getValue())
                        .threshold(evalReq.getThreshold())
                        .evaluateReason(evalReq.isEvaluateReason())
                        .build())

                .prompt(RunExperimentCommand.PromptConfig.builder()
                        .name(promptReq.getName())
                        .version(promptReq.getVersion())
                        .systemPrompt(promptReq.getSystemPrompt())
                        .build())

                .build();
    }
}
