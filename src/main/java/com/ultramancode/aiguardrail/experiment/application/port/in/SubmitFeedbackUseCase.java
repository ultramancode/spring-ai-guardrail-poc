package com.ultramancode.aiguardrail.experiment.application.port.in;

import com.ultramancode.aiguardrail.common.observability.command.RecordScoreCommand;

public interface SubmitFeedbackUseCase {
    void submitFeedback(RecordScoreCommand command);
}
