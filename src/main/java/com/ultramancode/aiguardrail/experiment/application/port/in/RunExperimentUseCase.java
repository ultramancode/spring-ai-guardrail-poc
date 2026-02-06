package com.ultramancode.aiguardrail.experiment.application.port.in;

import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.application.result.ExperimentResult;
import com.ultramancode.aiguardrail.experiment.application.result.HumanEvaluationResult;

public interface RunExperimentUseCase {
    ExperimentResult runExperiment(RunExperimentCommand command);

    HumanEvaluationResult aggregateHumanScores(String runName, String humanScoreName, String llmScoreName);
}
