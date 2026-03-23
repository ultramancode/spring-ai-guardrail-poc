package com.ultramancode.aiguardrail.experiment.application.port.in;

import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.application.result.ExperimentResult;

public interface RunExperimentUseCase {
    ExperimentResult runExperiment(RunExperimentCommand command);
}
