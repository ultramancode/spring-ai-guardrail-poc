package com.ultramancode.aiguardrail.experiment.application.command;

import com.ultramancode.aiguardrail.common.observability.domain.ScoreType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AggregateHumanScoresCommand {

    private String runName;

    @Builder.Default
    private String humanScoreName = ScoreType.USER_FEEDBACK.getValue();

    @Builder.Default
    private String autoScoreName = ScoreType.EXPERIMENT_SCORE.getValue();

    public void normalizeAndValidateOrThrow() {
        if (runName == null || runName.isBlank()) {
            throw new IllegalArgumentException("runName must not be blank.");
        }
        runName = runName.trim();

        humanScoreName = normalizeScoreNameOrDefault(humanScoreName, ScoreType.USER_FEEDBACK.getValue());
        autoScoreName = normalizeScoreNameOrDefault(autoScoreName, ScoreType.EXPERIMENT_SCORE.getValue());

        if (humanScoreName.equalsIgnoreCase(autoScoreName)) {
            throw new IllegalArgumentException(
                    "humanScoreName and autoScoreName(alias: llmScoreName) must be different."
            );
        }
    }

    private String normalizeScoreNameOrDefault(String scoreName, String defaultValue) {
        if (scoreName == null || scoreName.isBlank()) {
            return defaultValue;
        }
        return scoreName.trim();
    }
}
