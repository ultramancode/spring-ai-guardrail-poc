package com.ultramancode.aiguardrail.infrastructure.guardrail.pii;

import lombok.extern.slf4j.Slf4j;
import com.ultramancode.aiguardrail.application.guardrail.pii.PiiService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * Spring AI Advisor that implements PII tokenization for outgoing prompts.
 */
@Slf4j
@Component
public class PiiGuardrailAdvisor implements CallAdvisor, StreamAdvisor {

    private final PiiService piiService;

    public PiiGuardrailAdvisor(PiiService piiService) {
        this.piiService = piiService;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String originalPrompt = request.prompt().getUserMessage().getText();
        String tokenizedPrompt = piiService.tokenize(originalPrompt);

        // [Checkpoint 1: Outgoing Prompt]
        log.info("[GUARDRAIL] Original Prompt: \"{}\"", originalPrompt);
        log.info("[GUARDRAIL] Masked Input (For LLM): \"{}\"", tokenizedPrompt);

        ChatClientRequest updatedRequest = request.mutate()
                .prompt(request.prompt().augmentUserMessage(tokenizedPrompt))
                .build();

        ChatClientResponse response = chain.nextCall(updatedRequest);
        
        // [Checkpoint: LLM Response & Detokenization]
        if (response.chatResponse() != null && response.chatResponse().getResult() != null) {
             String rawOutput = response.chatResponse().getResult().getOutput().getText();
             log.info("[SERVER-IN] FROM LLM/CHAIN (Raw): {}", rawOutput);
             
             // Restore Real PII in the final response
             String detokenizedOutput = piiService.detokenize(rawOutput);
             log.info("[SERVER-OUT] TO USER (Detokenized): {}", detokenizedOutput);

             // Reconstruct response with detokenized content
             AssistantMessage newMsg = new AssistantMessage(detokenizedOutput);
             Generation newGen = new Generation(newMsg);
             ChatResponse newChatResponse = new ChatResponse(List.of(newGen));
             
             // Return new ChatClientResponse with detokenized content and preserved metadata (or empty map if simple)
             return new ChatClientResponse(newChatResponse, Collections.emptyMap());
        }
        
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // ============================================================
        // STREAMING BUFFERING STRATEGY
        // ============================================================
        // Problem: PII tokens may be split across chunks (e.g., [PERS + ON_1])
        // Solution: Buffer all chunks, merge, detokenize, then emit as single response
        // Trade-off: Real-time streaming display is sacrificed for correctness
        // ============================================================
        
        String originalPrompt = request.prompt().getUserMessage().getText();
        String tokenizedPrompt = piiService.tokenize(originalPrompt);

        log.info("[GUARDRAIL-STREAM] Original Prompt: \"{}\"", originalPrompt);
        log.info("[GUARDRAIL-STREAM] Masked Input (For LLM): \"{}\"", tokenizedPrompt);

        ChatClientRequest updatedRequest = request.mutate()
                .prompt(request.prompt().augmentUserMessage(tokenizedPrompt))
                .build();

        // Buffer all streaming chunks, then process as single response
        return chain.nextStream(updatedRequest)
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
                    
                    String rawOutput = fullOutput.toString();
                    log.info("[GUARDRAIL-STREAM] Buffered output: \"{}\"", rawOutput);
                    
                    // Detokenize the complete response
                    String detokenizedOutput = piiService.detokenize(rawOutput);
                    log.info("[GUARDRAIL-STREAM] Detokenized output: \"{}\"", detokenizedOutput);
                    
                    // Create single response with detokenized content
                    AssistantMessage newMsg = new AssistantMessage(detokenizedOutput);
                    Generation newGen = new Generation(newMsg);
                    ChatResponse newChatResponse = new ChatResponse(List.of(newGen));
                    ChatClientResponse finalResponse = new ChatClientResponse(newChatResponse, Collections.emptyMap());
                    
                    return Flux.just(finalResponse);
                });
    }

    @Override
    public String getName() {
        return "PiiGuardrailAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
