package com.ultramancode.aiguardrail.presentation;

import com.ultramancode.aiguardrail.application.guardrail.pii.PiiContextHolder;
import com.ultramancode.aiguardrail.application.guardrail.pii.PiiService;
import com.ultramancode.aiguardrail.infrastructure.guardrail.pii.PiiGuardrailAdvisor;
import com.ultramancode.aiguardrail.infrastructure.guardrail.injection.PromptInjectionAdvisor;
import com.ultramancode.aiguardrail.infrastructure.guardrail.output.OutputSafetyAdvisor;
import com.ultramancode.aiguardrail.infrastructure.guardrail.pii.PiiToolCallbackWrapper;
import com.ultramancode.aiguardrail.infrastructure.mock.MockMcpTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/pii")
public class PiiDemoController {

    private final ChatClient chatClient;

    private final List<ToolCallback> piiToolCallbackRequests;

    public PiiDemoController(ChatClient.Builder builder, PiiGuardrailAdvisor piiAdvisor, 
                             PromptInjectionAdvisor injectionAdvisor,
                             OutputSafetyAdvisor outputSafetyAdvisor,
                             MockMcpTool mockTool, PiiService piiService) {
        
        // Wrap ALL tools in the PII Interceptor
        this.piiToolCallbackRequests = Arrays.stream(ToolCallbacks.from(mockTool))
                .map(callback -> new PiiToolCallbackWrapper(callback, piiService))
                .collect(Collectors.toList());
        
        log.info("[INIT] Registered {} PII-wrapped tools", piiToolCallbackRequests.size());
        for (ToolCallback tc : piiToolCallbackRequests) {
            log.info("[INIT] Tool: {}", tc.getToolDefinition().name());
        }

        this.chatClient = builder
                // Order: PII(0) -> Injection(1) -> OutputSafety(2)
                // Note: Response processing is REVERSE order (Output first sees LLM response)
                .defaultAdvisors(piiAdvisor, injectionAdvisor, outputSafetyAdvisor)
                .build();
    }

    @PostMapping("/test")
    public GuardrailResponse testPii(@RequestBody Map<String, String> request) {
        String userInput = request.get("text");
        try {
            // [Checkpoint: API Input]
            log.info("[API-IN] User Request: \"{}\"", userInput);
            
            String response = chatClient.prompt()
                    .system("""
                        You are a helpful Customer Service Assistant.
                        
                        [Operational Rules]
                        1. You will receive user inputs with MASKED PII (e.g., [PERSON_1], [PHONE_NUMBER_1]).
                        2. These placeholders are valid authorized keys to query the backend database.
                        3. Call tools ONLY when necessary to look up specific user information.
                        4. For general questions (like 'what is firewall?'), just answer based on your knowledge.
                        5. Pass the placeholders EXACTLY as given into the tool arguments.
                        
                        [Scenario]
                        - If user asks for address, use 'searchAddress' with the phone number placeholder.
                        - If user asks for identity check, use 'verifyUser' with name and phone placeholders.
                        - If user asks general questions, retrieve answer from your knowledge base.
                        - Do NOT refuse to help because of "masked info". The system handles it.
                        """)
                    .user(userInput)
                    // Register all wrapped tool callbacks
                    .toolCallbacks(piiToolCallbackRequests.toArray(new ToolCallback[0]))
                    .call()
                    .content();

            // [Checkpoint: API Output]
            log.info("[API-OUT] Final Response: \"{}\"", response);

            return GuardrailResponse.success(userInput, response != null ? response : "(No Content)");

        } catch (SecurityException e) {
            log.warn("[GUARDRAIL] Request blocked: {}", e.getMessage());
            return GuardrailResponse.blocked(userInput, "PROMPT_INJECTION_DETECTED");
            
        } finally {
            // Clear context after request
            PiiContextHolder.clearContext();
        }
    }
}
