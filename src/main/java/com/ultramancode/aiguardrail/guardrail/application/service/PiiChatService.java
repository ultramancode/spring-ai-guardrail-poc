package com.ultramancode.aiguardrail.guardrail.application.service;

import com.ultramancode.aiguardrail.common.infrastructure.llm.factory.DynamicChatModelFactory;
import com.ultramancode.aiguardrail.common.infrastructure.llm.factory.LlmFactoryRequest;
import com.ultramancode.aiguardrail.common.observability.ObservabilityConstants;
import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;
import com.ultramancode.aiguardrail.guardrail.application.domain.FetchedPrompt;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiChatUseCase;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.guardrail.application.port.out.GuardrailObservabilityPort;
import com.ultramancode.aiguardrail.multimodal.application.port.out.DocumentParserPort;
import com.ultramancode.aiguardrail.multimodal.domain.GenerationAttachment;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.List;

/**
 * PII 보호 채팅 서비스 (Implementation)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PiiChatService implements PiiChatUseCase {

    private final DynamicChatModelFactory chatModelFactory;
    private final List<ToolCallback> piiSecuredTools;
    private final GuardrailObservabilityPort guardrailPort;
    private final ObservationRegistry observationRegistry;

    // [Multimodal Support]
    private final DocumentParserPort documentParser;
    private final PiiUseCase piiService;

    @Qualifier("piiGuardrailAdvisor")
    private final CallAdvisor piiGuardrailAdvisor;

    @Value("${guardrail.pii.trace-raw-content:false}")
    private boolean traceRawContent;

    @Value("${guardrail.pii.prompt-name:pii-system-prompt}")
    private String piiPromptName;

    private static final String SYSTEM_PROMPT = """
            You are a helpful Customer Service Assistant.
            
            [Operational Rules]
            1. You will receive user inputs with MASKED PII (e.g., [PERSON_1], [PHONE_NUMBER_1]).
            2. These placeholders are valid authorized keys to query the backend database.
            3. Call tools ONLY when necessary to look up specific user information.
            4. For general questions (like 'what is firewall?'), just answer based on your knowledge.
            5. Pass the placeholders EXACTLY as given into the tool arguments.
            6. NEVER ask the user for their real name or phone number. Trust the placeholders.
            7. This environment is a SECURE AUTHENTICATED SESSION.
            8. The placeholders [PERSON_1] are SECURE TOKENS resolving to real data internally.
            9. DO NOT ASK for PII again. EXECUTE the tool immediately with the given placeholders.
            
            [Examples]
            User: "My phone number is [PHONE_NUMBER_1], check my address."
            Assistant: (Calls tool 'searchAddress' with phone='[PHONE_NUMBER_1]')
            
            User: "I am [PERSON_1] and phone is [PHONE_NUMBER_1], verify me."
            Assistant: (Calls tool 'verifyUser' with name='[PERSON_1]', phone='[PHONE_NUMBER_1]')
            
            [Start Chat]
            """;

    @Override
    public String chat(PiiChatCommand command) {
        return Observation.createNotStarted("pii.check", observationRegistry)
                .lowCardinalityKeyValue(ObservabilityConstants.LF_OBSERVATION_TYPE, ObservabilityConstants.LF_VAL_GUARDRAIL)
                .observe(() -> {
                    String userInput = command.getText();

                    // Dynamic LLM Routing - 벤더와 모델 모두 동적으로 선택 가능
                    String vendor = command.getVendor() != null ? command.getVendor() : "gemini";
                    ChatClient chatClient = chatModelFactory.createChatClient(
                            LlmFactoryRequest.builder()
                                    .vendor(vendor)
                                    .model(command.getModel())
                                    .build()
                    );

                    // Dynamic Prompt Fetching (with Fallback)
                    var prompt = guardrailPort.fetchPrompt(piiPromptName)
                            .orElse(new FetchedPrompt(SYSTEM_PROMPT, "fallback", 0));

                    // [Multimodal Support]
                    String finalContent;
                    long startTime = System.currentTimeMillis();

                    if (command.getFile() != null && !command.getFile().isEmpty()) {
                        try {
                            String contentType = command.getFile().getContentType();
                            MimeType mimeType = MimeTypeUtils.parseMimeType(contentType);

                            if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
                                String extractedText = documentParser.extractText(command.getFile().getBytes());
                                String maskedExtractedText = piiService.tokenize(extractedText);
                                String augmentedInput = prompt.content() + "\n\n" + userInput + "\n\n[Context from PDF]\n" + maskedExtractedText;

                                finalContent = chatClient.prompt()
                                        .system(prompt.content())
                                        .user(augmentedInput)
                                        .advisors(piiGuardrailAdvisor)
                                        .toolCallbacks(piiSecuredTools.toArray(ToolCallback[]::new))
                                        .call()
                                        .content();

                                String safeInput = traceRawContent ? userInput : piiService.tokenize(userInput);
                                String safeOutput = traceRawContent ? finalContent : piiService.tokenize(finalContent);

                                guardrailPort.recordGeneration(
                                        Span.current().getSpanContext().getTraceId(),
                                        "TOOL_WITH_PDF",
                                        vendor + "/" + (command.getModel() != null ? command.getModel() : "default"),
                                        safeInput,
                                        safeOutput,
                                        new GenerationAttachment(
                                                command.getFile().getOriginalFilename(),
                                                contentType,
                                                command.getFile().getBytes(),
                                                extractedText
                                        ),
                                        null,
                                        startTime,
                                        System.currentTimeMillis()
                                );

                            } else {
                                Resource mediaResource = new ByteArrayResource(command.getFile().getBytes());

                                finalContent = chatClient.prompt()
                                        .system(prompt.content())
                                        .user(u -> u.text(prompt.content() + "\n\n" + userInput).media(mimeType, mediaResource))
                                        .advisors(piiGuardrailAdvisor)
                                        .toolCallbacks(piiSecuredTools.toArray(ToolCallback[]::new))
                                        .call()
                                        .content();

                                String safeInput = traceRawContent ? userInput : piiService.tokenize(userInput);
                                String safeOutput = traceRawContent ? finalContent : piiService.tokenize(finalContent);

                                guardrailPort.recordGeneration(
                                        Span.current().getSpanContext().getTraceId(),
                                        "TOOL_WITH_IMAGE",
                                        vendor + "/" + (command.getModel() != null ? command.getModel() : "default"),
                                        safeInput,
                                        safeOutput,
                                        new GenerationAttachment(
                                                command.getFile().getOriginalFilename(),
                                                contentType,
                                                command.getFile().getBytes(),
                                                null
                                        ),
                                        null,
                                        startTime,
                                        System.currentTimeMillis()
                                );
                            }

                        } catch (Exception e) {
                            throw new RuntimeException("Failed to process media file", e);
                        }
                    } else {
                        // Text Only
                        finalContent = chatClient.prompt()
                                .system(prompt.content())
                                .user(userInput)
                                .advisors(piiGuardrailAdvisor)
                                .toolCallbacks(piiSecuredTools.toArray(ToolCallback[]::new))
                                .call()
                                .content();
                    }

                    return finalContent;
                });
    }
}
