package com.ultramancode.aiguardrail.guardrail.infrastructure.advisor;

import com.ultramancode.aiguardrail.guardrail.application.port.in.PromptInjectionUseCase;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromptInjectionAdvisor implements CallAdvisor, StreamAdvisor {

    private final PromptInjectionUseCase promptInjectionService;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String tokenizedInput = request.prompt().getUserMessage().getText();
        checkSecurity(tokenizedInput);
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String tokenizedInput = request.prompt().getUserMessage().getText();
        checkSecurity(tokenizedInput);
        return chain.nextStream(request);
    }

    private void checkSecurity(String tokenizedInput) {
        // Capture current Trace ID from OTel (propagated via MDC or Span)
        String traceId = Span.current().getSpanContext().getTraceId();

        // Delegate to Application Service
        promptInjectionService.checkSecurity(tokenizedInput, traceId);
    }

    @Override
    public String getName() {
        return "PromptInjectionAdvisor";
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
