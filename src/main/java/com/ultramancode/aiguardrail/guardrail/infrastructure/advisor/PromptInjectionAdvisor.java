package com.ultramancode.aiguardrail.guardrail.infrastructure.advisor;

import com.ultramancode.aiguardrail.common.observability.TraceIdResolver;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PromptInjectionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromptInjectionAdvisor implements CallAdvisor {

    private final PromptInjectionUseCase promptInjectionService;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String tokenizedInput = resolveTokenizedInput(request);
        checkSecurity(tokenizedInput);
        return chain.nextCall(request);
    }

    private String resolveTokenizedInput(ChatClientRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PromptInjectionAdvisor request must not be null.");
        }

        Prompt prompt = request.prompt();
        if (prompt == null) {
            throw new IllegalArgumentException("PromptInjectionAdvisor prompt must not be null.");
        }

        UserMessage userMessage = prompt.getUserMessage();
        if (userMessage == null) {
            throw new IllegalArgumentException("PromptInjectionAdvisor user message must not be null.");
        }

        String text = userMessage.getText();
        if (text == null) {
            throw new IllegalArgumentException("PromptInjectionAdvisor user message text must not be null.");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("PromptInjectionAdvisor user message text must not be blank.");
        }
        return text;
    }

    private void checkSecurity(String tokenizedInput) {
        // 현재 OTel span에서 traceId를 추출합니다.
        String traceId = TraceIdResolver.currentTraceIdOrNull();

        // 애플리케이션 레이어 보안 검사 서비스를 호출합니다.
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
