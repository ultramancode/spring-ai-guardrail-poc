package com.ultramancode.aiguardrail.guardrail.infrastructure.advisor;

import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.observability.infrastructure.utils.TraceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Collection;
import java.util.Map;
import java.lang.reflect.Constructor;

import org.springframework.ai.content.Media;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

/**
 * Spring AI Advisor that implements PII tokenization for outgoing prompts.
 */
@Slf4j
@Component
public class PiiGuardrailAdvisor implements CallAdvisor, StreamAdvisor {

    private final PiiUseCase piiService;
    private final boolean traceRawContent;

    public PiiGuardrailAdvisor(PiiUseCase piiService,
                               @Value("${guardrail.pii.trace-raw-content:false}") boolean traceRawContent) {
        this.piiService = piiService;
        this.traceRawContent = traceRawContent;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String originalPrompt = request.prompt().getUserMessage().getText();
        String tokenizedPrompt = piiService.tokenize(originalPrompt);

        // [Secure Media Handling]
        // If the original message had media (Multimodal), we must preserve it.
        // request.prompt().getUserMessage() -> might be UserMessage
        UserMessage newUserMessage;
        if (request.prompt().getUserMessage().getMedia().isEmpty()) {
            // Text-only
            newUserMessage = new UserMessage(tokenizedPrompt);
        } else {
            // Multimodal: Create new UserMessage with tokenized text + original media
            try {
                List<Media> mediaList = request.prompt().getUserMessage().getMedia();

                // Use Reflection to access private/protected constructor: UserMessage(String, Collection<Media>, Map)
                Constructor<UserMessage> ctor =
                        UserMessage.class.getDeclaredConstructor(
                                String.class, Collection.class, Map.class);
                ctor.setAccessible(true);

                newUserMessage = ctor.newInstance(tokenizedPrompt, mediaList, Collections.emptyMap());
            } catch (Exception e) {
                // Fallback to text-only if reflection fails (should not happen if versions match)
                log.warn("[PiiGuardrailAdvisor] Failed to construct multimodal UserMessage via reflection: {}", e.getMessage());
                newUserMessage = new UserMessage(tokenizedPrompt);
            }
        }

        // Secure Tracing: Tag with tokenized prompt unless raw content is explicitly requested
        if (traceRawContent) {
            TraceUtils.tagSpanInput(originalPrompt);
        } else {
            TraceUtils.tagSpanInput(tokenizedPrompt);
        }

        ChatClientRequest updatedRequest = request.mutate()
                .prompt(new org.springframework.ai.chat.prompt.Prompt(newUserMessage, request.prompt().getOptions())) // Replace UserMessage completely
                .build();

        ChatClientResponse response = chain.nextCall(updatedRequest);

        // [Checkpoint: LLM Response & Detokenization]
        if (response.chatResponse() != null && response.chatResponse().getResult() != null) {
            String rawOutput = response.chatResponse().getResult().getOutput().getText();
            log.info("[SERVER-IN] FROM LLM/CHAIN (Raw): {}", rawOutput);

            // Restore Real PII in the final response
            String detokenizedOutput = piiService.detokenize(rawOutput);

            // Secure Tracing: Tag with detokenized (masked-original) or raw depending on mode
            if (traceRawContent) {
                TraceUtils.tagSpanOutput(detokenizedOutput);
            } else {
                TraceUtils.tagSpanOutput(rawOutput); // rawOutput here is actually masked because it's from LLM
            }

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
