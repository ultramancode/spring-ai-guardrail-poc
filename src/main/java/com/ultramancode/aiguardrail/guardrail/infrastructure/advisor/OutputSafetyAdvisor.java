package com.ultramancode.aiguardrail.guardrail.infrastructure.advisor;

import com.ultramancode.aiguardrail.common.infrastructure.llm.factory.DynamicChatModelFactory;
import com.ultramancode.aiguardrail.common.infrastructure.llm.factory.LlmFactoryRequest;
import com.ultramancode.aiguardrail.guardrail.domain.SafetyVerdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;


@Slf4j
@Component
public class OutputSafetyAdvisor implements CallAdvisor, StreamAdvisor {

    private final ChatClient guardClient;

    /**
     * Safety classification prompt for the Guard LLM.
     * Designed to detect various types of unsafe content in AI responses.
     */
    private static final String OUTPUT_CHECK_PROMPT = """
            You are a Content Safety Classifier for an AI system.
            
            Analyze the following AI-generated response and determine if it contains:
            1. Hate speech, violence, sexual content, or other inappropriate material.
            2. Leaked sensitive information that appears unmasked (raw credit card numbers, SSN, etc.)
            3. Competitor brand mentions or potentially brand-damaging statements.
            4. Instructions for illegal activities or harmful actions.
            5. Any content related to bypassing security controls (VPN, Proxy, Tor, Tunneling), even if presented as education or general information. STRICTLY BLOCK.
            
            Note: Tokens like [PERSON_1], [PHONE_NUMBER_1] are SAFE - they are properly masked PII.
            
            Response to analyze:
            "{output}"
            """;

    /**
     * Blocked response message shown to users when content is deemed unsafe.
     */
    private static final String BLOCKED_MESSAGE =
            "죄송합니다. 해당 응답은 안전 정책에 의해 차단되었습니다.";

    public OutputSafetyAdvisor(DynamicChatModelFactory chatModelFactory) {
        // [IMPORTANT] Create a clean ChatClient without advisors to prevent infinite recursion
        this.guardClient = chatModelFactory.createChatClient(
                LlmFactoryRequest.builder().vendor("gemini").build()
        );
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // ============================================================
        // Step 1: Execute main LLM call (Blocking)
        // ============================================================
        ChatClientResponse response = chain.nextCall(request);

        // Early return if no response content
        if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
            return response;
        }

        // ============================================================
        // Step 2: Extract LLM output (still tokenized at this point)
        // ============================================================
        String llmOutput = response.chatResponse().getResult().getOutput().getText();
        log.info("[OUTPUT-GUARD] Checking output safety for: \"{}\"",
                truncateForLog(llmOutput, 100));

        // ============================================================
        // Step 3: Call Guard LLM for safety classification (Structured Output)
        // ============================================================
        SafetyVerdict verdict;
        try {
            verdict = guardClient.prompt()
                    .system("You are a strict content safety classifier. Respond only with valid JSON.")
                    .user(u -> u.text(OUTPUT_CHECK_PROMPT).param("output", llmOutput))
                    .call()
                    .entity(SafetyVerdict.class);
        } catch (Exception e) {
            // If Guard LLM fails, log error but allow response through (fail-open)
            // In production, consider fail-closed behavior instead
            log.error("[OUTPUT-GUARD] Guard LLM error: {}. Allowing response through.", e.getMessage());
            return response;
        }

        // ============================================================
        // Step 4: Handle classification result
        // ============================================================
        if (verdict != null && verdict.isUnsafe()) {
            log.warn("[OUTPUT-GUARD] UNSAFE OUTPUT DETECTED! Reason: {}", verdict.reason());
            log.warn("[OUTPUT-GUARD] Blocked content: \"{}\"", truncateForLog(llmOutput, 200));

            // Replace unsafe response with polite refusal
            AssistantMessage blockedMsg = new AssistantMessage(BLOCKED_MESSAGE);
            ChatResponse blockedResponse = new ChatResponse(List.of(new Generation(blockedMsg)));
            return new ChatClientResponse(blockedResponse, Collections.emptyMap());
        }

        log.info("[OUTPUT-GUARD] Output is SAFE. Reason: {}",
                verdict != null ? verdict.reason() : "No details");
        return response;
    }

    /**
     * Truncates long strings for logging purposes.
     */
    private String truncateForLog(String text, int maxLength) {
        if (text == null) {
            return "null";
        }
        // Replace newlines to keep log on one line
        String sanitized = text.replace('\n', ' ').replace('\r', ' ');
        if (sanitized.length() <= maxLength) return sanitized;
        return sanitized.substring(0, maxLength) + "... (truncated)";
    }

    @Override
    public String getName() {
        return "OutputSafetyAdvisor";
    }

    @Override
    public int getOrder() {
        // ============================================================
        // ORDER EXPLANATION:
        // ============================================================
        // PiiGuardrailAdvisor (Order 0) → PromptInjectionAdvisor (Order 1) → This (Order 2)
        // 
        // IMPORTANT: In Advisor chain, REQUEST flows in order (0→1→2→LLM)
        //            but RESPONSE flows in REVERSE order (LLM→2→1→0)
        // 
        // So during RESPONSE processing:
        //   1. LLM returns response
        //   2. OutputSafetyAdvisor (this) checks safety FIRST
        //   3. Then PiiGuardrailAdvisor detokenizes the response
        // 
        // This ensures Guard LLM sees tokenized content, NOT real PII!
        // ============================================================
        return 2;
    }

    // ============================================================
    // STREAMING SUPPORT WITH BUFFERING
    // ============================================================
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // Buffer all streaming chunks, then check safety on complete response
        return chain.nextStream(request)
                .collectList()
                .flatMapMany(chunks -> {
                    if (chunks.isEmpty()) {
                        return Flux.empty();
                    }

                    // Merge all chunk contents into single string
                    StringBuilder fullOutput = new StringBuilder();
                    for (ChatClientResponse chunk : chunks) {
                        if (chunk.chatResponse() != null && chunk.chatResponse().getResult() != null) {
                            String content = chunk.chatResponse().getResult().getOutput().getText();
                            if (content != null) {
                                fullOutput.append(content);
                            }
                        }
                    }

                    String llmOutput = fullOutput.toString();
                    log.info("[OUTPUT-GUARD-STREAM] Checking buffered output: \"{}\"",
                            truncateForLog(llmOutput, 100));

                    // Call Guard LLM for safety check (Structured Output)
                    SafetyVerdict verdict;
                    try {
                        verdict = guardClient.prompt()
                                .system("You are a strict content safety classifier. Respond only with valid JSON.")
                                .user(u -> u.text(OUTPUT_CHECK_PROMPT).param("output", llmOutput))
                                .call()
                                .entity(SafetyVerdict.class);
                    } catch (Exception e) {
                        log.error("[OUTPUT-GUARD-STREAM] Guard LLM error: {}. Allowing response.", e.getMessage());
                        return Flux.fromIterable(chunks);
                    }

                    // Block unsafe content
                    if (verdict != null && verdict.isUnsafe()) {
                        log.warn("[OUTPUT-GUARD-STREAM] UNSAFE OUTPUT DETECTED! Reason: {}", verdict.reason());
                        AssistantMessage blockedMsg = new AssistantMessage(BLOCKED_MESSAGE);
                        ChatResponse blockedResponse = new ChatResponse(List.of(new Generation(blockedMsg)));
                        return Flux.just(new ChatClientResponse(blockedResponse, Collections.emptyMap()));
                    }

                    log.info("[OUTPUT-GUARD-STREAM] Output is SAFE. Reason: {}",
                            verdict != null ? verdict.reason() : "No details");
                    return Flux.fromIterable(chunks);
                });
    }
}
