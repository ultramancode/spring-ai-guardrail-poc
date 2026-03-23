package com.ultramancode.aiguardrail.guardrail.infrastructure.support;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;

class GuardrailAdvisorChainFactoryTest {

    @Test
    void build_returnsInjectionChain_whenModeIsInjection() {
        CallAdvisor piiAdvisor = mock(CallAdvisor.class);
        CallAdvisor promptInjectionAdvisor = mock(CallAdvisor.class);
        CallAdvisor outputSafetyAdvisor = mock(CallAdvisor.class);
        GuardrailAdvisorChainFactory factory = new GuardrailAdvisorChainFactory(
                piiAdvisor,
                promptInjectionAdvisor,
                outputSafetyAdvisor
        );

        CallAdvisor[] advisors = factory.build("injection");

        assertArrayEquals(new CallAdvisor[]{piiAdvisor, promptInjectionAdvisor}, advisors);
    }

    @Test
    void build_returnsOutputChain_whenModeIsOutput() {
        CallAdvisor piiAdvisor = mock(CallAdvisor.class);
        CallAdvisor promptInjectionAdvisor = mock(CallAdvisor.class);
        CallAdvisor outputSafetyAdvisor = mock(CallAdvisor.class);
        GuardrailAdvisorChainFactory factory = new GuardrailAdvisorChainFactory(
                piiAdvisor,
                promptInjectionAdvisor,
                outputSafetyAdvisor
        );

        CallAdvisor[] advisors = factory.build("output");

        assertArrayEquals(new CallAdvisor[]{piiAdvisor, outputSafetyAdvisor}, advisors);
    }

    @Test
    void build_returnsFullChain_whenModeIsFull() {
        CallAdvisor piiAdvisor = mock(CallAdvisor.class);
        CallAdvisor promptInjectionAdvisor = mock(CallAdvisor.class);
        CallAdvisor outputSafetyAdvisor = mock(CallAdvisor.class);
        GuardrailAdvisorChainFactory factory = new GuardrailAdvisorChainFactory(
                piiAdvisor,
                promptInjectionAdvisor,
                outputSafetyAdvisor
        );

        CallAdvisor[] advisors = factory.build("full");

        assertArrayEquals(new CallAdvisor[]{piiAdvisor, promptInjectionAdvisor, outputSafetyAdvisor}, advisors);
    }

    @Test
    void build_returnsDefaultFullChain_whenModeIsNull() {
        CallAdvisor piiAdvisor = mock(CallAdvisor.class);
        CallAdvisor promptInjectionAdvisor = mock(CallAdvisor.class);
        CallAdvisor outputSafetyAdvisor = mock(CallAdvisor.class);
        GuardrailAdvisorChainFactory factory = new GuardrailAdvisorChainFactory(
                piiAdvisor,
                promptInjectionAdvisor,
                outputSafetyAdvisor
        );

        CallAdvisor[] advisors = factory.build(null);

        assertArrayEquals(new CallAdvisor[]{piiAdvisor, promptInjectionAdvisor, outputSafetyAdvisor}, advisors);
    }
}
