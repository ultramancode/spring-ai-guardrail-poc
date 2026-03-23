package com.ultramancode.aiguardrail.guardrail.infrastructure.adapter.experiment;

import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentChatPort;
import com.ultramancode.aiguardrail.guardrail.application.port.in.GuardrailChatFacadeUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ExperimentChatPort를 Guardrail 채팅 유스케이스에 연결하는 어댑터.
 */
@Component
@RequiredArgsConstructor
public class GuardrailExperimentChatAdapter implements ExperimentChatPort {

    private final GuardrailChatFacadeUseCase guardrailChatFacadeUseCase;

    @Override
    public ExperimentChatResult chat(ExperimentChatRequest request) {
        GuardrailChatFacadeUseCase.GuardrailChatFacadeResult result = guardrailChatFacadeUseCase.chat(
                new GuardrailChatFacadeUseCase.GuardrailChatFacadeCommand(
                        request.text(),
                        request.file(),
                        request.vendor(),
                        request.model(),
                        request.targetGuardrail(),
                        request.systemPrompt()
                )
        );

        return new ExperimentChatResult(result.output(), result.observationId());
    }
}

