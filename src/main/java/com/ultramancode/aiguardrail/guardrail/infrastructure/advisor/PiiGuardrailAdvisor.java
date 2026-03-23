package com.ultramancode.aiguardrail.guardrail.infrastructure.advisor;

import com.ultramancode.aiguardrail.common.observability.TraceUtils;
import com.ultramancode.aiguardrail.common.util.ChatResponseTextExtractor;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 사용자 입력의 PII를 토큰화해 LLM에 전달하고,
 * 모델 응답을 디토큰화해 사용자에게 반환하는 Advisor입니다.
 */
@Slf4j
@Component
public class PiiGuardrailAdvisor implements CallAdvisor {

    private static final String TAG_MULTIMODAL_FALLBACK_APPLIED = "guardrail.multimodal_fallback_applied";
    private static final String TAG_MULTIMODAL_FALLBACK_REASON = "guardrail.multimodal_fallback_reason";

    private final PiiUseCase piiService;
    private final boolean traceRawContent;
    private final boolean multimodalFallbackEnabled;

    public PiiGuardrailAdvisor(
            PiiUseCase piiService,
            @Value("${guardrail.pii.trace-raw-content:false}") boolean traceRawContent,
            @Value("${guardrail.pii.multimodal-fallback-enabled:false}") boolean multimodalFallbackEnabled
    ) {
        this.piiService = piiService;
        this.traceRawContent = traceRawContent;
        this.multimodalFallbackEnabled = multimodalFallbackEnabled;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        TokenizedRequest tokenizedRequest = prepareTokenizedRequest(request, true);
        ChatClientResponse response = chain.nextCall(tokenizedRequest.updatedRequest());

        String rawOutput = ChatResponseTextExtractor.extract(response);
        if (rawOutput == null) {
            return response;
        }

        if (traceRawContent) {
            log.info("[SERVER-IN] FROM LLM/CHAIN (Raw): {}", rawOutput);
        } else {
            log.info("[SERVER-IN] FROM LLM/CHAIN (Raw): [masked] length={}", rawOutput.length());
        }

        String detokenizedOutput = piiService.detokenize(rawOutput);
        if (traceRawContent) {
            TraceUtils.tagSpanOutput(detokenizedOutput);
        } else {
            TraceUtils.tagSpanOutput(rawOutput);
        }

        if (traceRawContent) {
            log.info("[SERVER-OUT] TO USER (Detokenized): {}", detokenizedOutput);
        } else {
            int detokenizedLength = detokenizedOutput != null ? detokenizedOutput.length() : 0;
            log.info("[SERVER-OUT] TO USER (Detokenized): [masked] length={}", detokenizedLength);
        }
        return rebuildResponseWithDetokenizedOutput(response, detokenizedOutput);
    }

    private TokenizedRequest prepareTokenizedRequest(ChatClientRequest request, boolean tagInputTrace) {
        String originalPrompt = resolveOriginalPrompt(request);
        if (originalPrompt == null) {
            log.warn("[PiiGuardrailAdvisor] User message is missing. Skip PII tokenization for this request.");
            return new TokenizedRequest(request);
        }

        String tokenizedPrompt = piiService.tokenize(originalPrompt);

        Prompt updatedPrompt = request.prompt().augmentUserMessage(
                userMessage -> buildTokenizedUserMessage(userMessage, tokenizedPrompt)
        );
        ChatClientRequest updatedRequest = request.mutate()
                .prompt(updatedPrompt)
                .build();

        if (tagInputTrace) {
            if (traceRawContent) {
                TraceUtils.tagSpanInput(originalPrompt);
            } else {
                TraceUtils.tagSpanInput(tokenizedPrompt);
            }
        }

        return new TokenizedRequest(updatedRequest);
    }

    private String resolveOriginalPrompt(ChatClientRequest request) {
        try {
            Prompt prompt = request.prompt();
            if (prompt == null) {
                return null;
            }

            UserMessage userMessage = prompt.getUserMessage();
            if (userMessage == null) {
                return null;
            }

            String text = userMessage.getText();
            if (text == null) {
                return "";
            }
            return text;
        } catch (RuntimeException e) {
            log.warn("[PiiGuardrailAdvisor] Failed to resolve user message from prompt. cause={}", e.getMessage(), e);
            return null;
        }
    }

    private ChatClientResponse rebuildResponseWithDetokenizedOutput(
            ChatClientResponse originalResponse,
            String detokenizedOutput
    ) {
        AssistantMessage newMsg = new AssistantMessage(detokenizedOutput);
        ChatResponse originalChatResponse = originalResponse.chatResponse();

        if (originalChatResponse == null || originalChatResponse.getResult() == null) {
            Generation fallbackGeneration = new Generation(newMsg);
            ChatResponse fallbackResponse = new ChatResponse(List.of(fallbackGeneration));
            return originalResponse.mutate()
                    .chatResponse(fallbackResponse)
                    .build();
        }

        Generation originalGeneration = originalChatResponse.getResult();
        Generation newGeneration;
        if (originalGeneration.getMetadata() != null) {
            newGeneration = new Generation(newMsg, originalGeneration.getMetadata());
        } else {
            newGeneration = new Generation(newMsg);
        }

        ChatResponse newChatResponse;
        if (originalChatResponse.getMetadata() != null) {
            newChatResponse = new ChatResponse(List.of(newGeneration), originalChatResponse.getMetadata());
        } else {
            newChatResponse = new ChatResponse(List.of(newGeneration));
        }

        return originalResponse.mutate()
                .chatResponse(newChatResponse)
                .build();
    }

    private UserMessage buildTokenizedUserMessage(UserMessage originalUserMessage, String tokenizedPrompt) {
        try {
            return originalUserMessage.mutate()
                    .text(tokenizedPrompt)
                    .build();
        } catch (RuntimeException e) {
            if (!multimodalFallbackEnabled) {
                throw new IllegalStateException("Failed to construct multimodal UserMessage", e);
            }

            // POC 정책: 멀티모달 payload 생성 실패 시에도 텍스트 질의는 계속 처리한다.
            log.warn(
                    "[PiiGuardrailAdvisor] Multimodal message construction failed. Fallback to text-only mode. reason={}",
                    e.getMessage(),
                    e
            );
            Span.current().setAttribute(TAG_MULTIMODAL_FALLBACK_APPLIED, true);
            Span.current().setAttribute(
                    TAG_MULTIMODAL_FALLBACK_REASON,
                    e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "unknown" : e.getMessage())
            );
            return new UserMessage(tokenizedPrompt);
        }
    }

    @Override
    public String getName() {
        return "PiiGuardrailAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private record TokenizedRequest(
            ChatClientRequest updatedRequest
    ) {
    }
}
