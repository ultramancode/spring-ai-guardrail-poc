package com.ultramancode.aiguardrail.experiment.application.usecase.feedback.service;

import com.ultramancode.aiguardrail.common.observability.command.RecordScoreCommand;
import com.ultramancode.aiguardrail.experiment.application.port.in.SubmitFeedbackUseCase;
import com.ultramancode.aiguardrail.experiment.application.port.out.EvaluationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubmitFeedbackService implements SubmitFeedbackUseCase {

    private final EvaluationRepositoryPort evaluationRepositoryPort;

    @Override
    public void submitFeedback(RecordScoreCommand command) {
        evaluationRepositoryPort.recordScore(command);
    }
}
