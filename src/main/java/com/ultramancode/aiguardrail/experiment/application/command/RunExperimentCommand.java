package com.ultramancode.aiguardrail.experiment.application.command;

import com.ultramancode.aiguardrail.experiment.domain.ExperimentMode;
import com.ultramancode.aiguardrail.experiment.domain.ExperimentTarget;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RunExperimentCommand {

    // Command corresponds to the Request but purely for Business Logic

    private String datasetName;
    private String runName;
    private String modelLabel;

    @Builder.Default
    private ExperimentMode mode = ExperimentMode.PROMPT_ONLY;

    private ExperimentTarget target;

    private String label;

    @Builder.Default
    private FieldMapping fieldMapping = FieldMapping.builder().build();

    @Builder.Default
    private EvaluationConfig evaluation = EvaluationConfig.builder().build();

    @Builder.Default
    private PromptConfig prompt = PromptConfig.builder().build();

    @Builder.Default
    private String scoreName = "experiment-score";

    @Data
    @Builder
    public static class FieldMapping {
        @Builder.Default
        private String input = "input";
        @Builder.Default
        private String expected = "verdict";
        private String expectedReason;
    }

    @Data
    @Builder
    public static class EvaluationConfig {
        @Builder.Default
        private String type = "exact_match";
        @Builder.Default
        private double threshold = 0.7;
        @Builder.Default
        private boolean evaluateReason = true;
        @Builder.Default
        private boolean evaluateMasked = true;
        @Builder.Default
        private boolean evaluateDetokenized = false;
    }

    @Data
    @Builder
    public static class PromptConfig {
        private String name;
        private String version;
        private String systemPrompt;
    }
}
