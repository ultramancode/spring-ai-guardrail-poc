package com.ultramancode.aiguardrail.guardrail.infrastructure.support;

import com.ultramancode.aiguardrail.guardrail.domain.GuardrailChainMode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GuardrailAdvisorChainFactory {

    @Qualifier("piiGuardrailAdvisor")
    private final CallAdvisor piiGuardrailAdvisor;

    @Qualifier("promptInjectionAdvisor")
    private final CallAdvisor promptInjectionAdvisor;

    @Qualifier("outputSafetyAdvisor")
    private final CallAdvisor outputSafetyAdvisor;

    public CallAdvisor[] build(String targetGuardrail) {
        GuardrailChainMode mode = GuardrailChainMode.fromValueOrDefault(
                targetGuardrail,
                GuardrailChainMode.FULL
        );

        if (mode == GuardrailChainMode.INJECTION) {
            return new CallAdvisor[]{
                    piiGuardrailAdvisor,
                    promptInjectionAdvisor
            };
        }

        if (mode == GuardrailChainMode.OUTPUT) {
            return new CallAdvisor[]{
                    piiGuardrailAdvisor,
                    outputSafetyAdvisor
            };
        }

        return new CallAdvisor[]{
                piiGuardrailAdvisor,
                promptInjectionAdvisor,
                outputSafetyAdvisor
        };
    }
}
