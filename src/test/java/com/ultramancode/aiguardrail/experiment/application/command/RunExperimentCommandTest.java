package com.ultramancode.aiguardrail.experiment.application.command;

import com.ultramancode.aiguardrail.experiment.domain.model.EvaluationMethod;
import com.ultramancode.aiguardrail.experiment.domain.model.ExperimentDefaults;
import com.ultramancode.aiguardrail.experiment.domain.model.ExperimentMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunExperimentCommandTest {

    @Test
    void normalizeAndValidateOrThrow_normalizesFieldsAndBuildsDefaultRunName_whenRunNameIsBlank() {
        RunExperimentCommand command = RunExperimentCommand.builder()
                .datasetName("  dataset-a  ")
                .runName("   ")
                .build();

        command.normalizeAndValidateOrThrow();

        assertEquals("dataset-a", command.getDatasetName());
        assertNotNull(command.getRunName());
        assertTrue(command.getRunName().startsWith(ExperimentDefaults.DEFAULT_RUN_NAME_PREFIX));
        assertEquals(EvaluationMethod.EXACT_MATCH, command.getEvaluation().resolvedMethodOrThrow());
    }

    @Test
    void normalizeAndValidateOrThrow_throwsException_whenDatasetNameIsBlank() {
        RunExperimentCommand command = RunExperimentCommand.builder()
                .datasetName(" ")
                .runName("run-a")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                command::normalizeAndValidateOrThrow
        );

        assertEquals("Dataset name must not be blank.", exception.getMessage());
    }

    @Test
    void normalizeAndValidateOrThrow_throwsException_whenTargetIsRequiredForTargetOnlyMode() {
        RunExperimentCommand command = RunExperimentCommand.builder()
                .datasetName("dataset-a")
                .runName("run-a")
                .mode(ExperimentMode.TARGET_ONLY)
                .target(null)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                command::normalizeAndValidateOrThrow
        );

        assertEquals("Experiment target must not be null.", exception.getMessage());
    }

    @Test
    void normalizeAndValidateOrThrow_throwsException_whenEvaluationThresholdIsOutOfRange() {
        RunExperimentCommand.EvaluationConfig evaluationConfig = RunExperimentCommand.EvaluationConfig.builder()
                .threshold(1.5)
                .build();
        RunExperimentCommand command = RunExperimentCommand.builder()
                .datasetName("dataset-a")
                .runName("run-a")
                .evaluation(evaluationConfig)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                command::normalizeAndValidateOrThrow
        );

        assertEquals("Evaluation threshold must be between 0.0 and 1.0.", exception.getMessage());
    }
}
