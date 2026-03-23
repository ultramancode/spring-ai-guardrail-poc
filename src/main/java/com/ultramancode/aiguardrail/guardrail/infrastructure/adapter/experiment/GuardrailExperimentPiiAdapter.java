package com.ultramancode.aiguardrail.guardrail.infrastructure.adapter.experiment;

import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentPiiPort;
import com.ultramancode.aiguardrail.guardrail.application.port.in.GuardrailPiiFacadeUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 실험 모듈에서 사용할 PII 토큰화 구현체입니다.
 */
@Component
@RequiredArgsConstructor
public class GuardrailExperimentPiiAdapter implements ExperimentPiiPort {

    private final GuardrailPiiFacadeUseCase guardrailPiiFacadeUseCase;

    @Override
    public String tokenize(String text) {
        return guardrailPiiFacadeUseCase.tokenizeWithoutObservation(text);
    }

    @Override
    public void clearContext() {
        guardrailPiiFacadeUseCase.clearContext();
    }
}

