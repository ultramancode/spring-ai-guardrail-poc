package com.ultramancode.aiguardrail.experiment.application.command;

import com.ultramancode.aiguardrail.experiment.domain.model.ExperimentComparisonMode;
import com.ultramancode.aiguardrail.experiment.domain.model.ExperimentGuardrailMode;
import com.ultramancode.aiguardrail.experiment.domain.model.ExperimentMode;
import com.ultramancode.aiguardrail.experiment.domain.model.ExperimentTarget;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExperimentCommandParsers {

    public static ExperimentMode parseMode(String mode) {
        return ExperimentMode.fromValue(mode);
    }

    public static ExperimentTarget parseTarget(String target) {
        return ExperimentTarget.fromValue(target);
    }

    public static ExperimentComparisonMode parseComparisonMode(String comparisonMode) {
        return ExperimentComparisonMode.fromValue(comparisonMode);
    }

    public static String parseTargetGuardrail(String targetGuardrail) {
        return ExperimentGuardrailMode.fromValue(targetGuardrail).getValue();
    }
}
