package com.ultramancode.aiguardrail.guardrail.application.service;

import com.ultramancode.aiguardrail.guardrail.application.port.in.GuardrailPiiFacadeUseCase;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 가드레일 PII 파사드 구현체입니다.
 */
@Service
@RequiredArgsConstructor
public class GuardrailPiiFacadeService implements GuardrailPiiFacadeUseCase {

    private final PiiUseCase piiUseCase;

    @Override
    public String tokenizeWithoutObservation(String text) {
        return piiUseCase.tokenizeWithoutObservation(text);
    }

    @Override
    public void clearContext() {
        piiUseCase.clearContext();
    }
}
