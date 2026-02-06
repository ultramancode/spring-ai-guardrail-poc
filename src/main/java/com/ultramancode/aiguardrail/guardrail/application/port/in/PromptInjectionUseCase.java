package com.ultramancode.aiguardrail.guardrail.application.port.in;

public interface PromptInjectionUseCase {
    void checkSecurity(String input, String traceId);
}
