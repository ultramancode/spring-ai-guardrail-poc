package com.ultramancode.aiguardrail.experiment.application.port.in;

import com.ultramancode.aiguardrail.experiment.application.command.AggregateHumanScoresCommand;
import com.ultramancode.aiguardrail.experiment.application.result.HumanEvaluationResult;

public interface AggregateHumanScoresUseCase {
    HumanEvaluationResult aggregateHumanScores(AggregateHumanScoresCommand command);
}
