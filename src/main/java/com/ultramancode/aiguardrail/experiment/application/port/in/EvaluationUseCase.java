package com.ultramancode.aiguardrail.experiment.application.port.in;

import com.ultramancode.aiguardrail.experiment.application.command.EvaluationCommand;
import com.ultramancode.aiguardrail.experiment.application.result.EvaluationMatchResult;
import com.ultramancode.aiguardrail.experiment.application.result.ExperimentResult;

import java.util.List;

public interface EvaluationUseCase {


    EvaluationMatchResult evaluateMatch(EvaluationCommand command);

    ExperimentResult.ConfusionMatrix calculateConfusionMatrix(List<ExperimentResult.TestCaseResult> results);

    double calculateF1Score(ExperimentResult.ConfusionMatrix cm);

    Double calculateAverageReasonScore(List<ExperimentResult.TestCaseResult> results);
}
