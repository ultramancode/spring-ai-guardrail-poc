package com.ultramancode.aiguardrail.experiment.application.command;

import com.ultramancode.aiguardrail.experiment.domain.EvaluationMethod;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EvaluationCommand {
    private final String expected;     // Expected Output from dataset
    private final String actual;       // Actual Output from LLM
    private final EvaluationMethod method; // EXACT_MATCH, CONTAINS, LLM_JUDGE
    @Builder.Default
    private final double threshold = 0.7; // Threshold for fuzzy matching (LLM Judge)
}
