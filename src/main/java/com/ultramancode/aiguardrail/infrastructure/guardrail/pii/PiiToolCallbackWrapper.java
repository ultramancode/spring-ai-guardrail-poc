package com.ultramancode.aiguardrail.infrastructure.guardrail.pii;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import com.ultramancode.aiguardrail.application.guardrail.pii.PiiService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Wraps an existing ToolCallback to transparently detokenize arguments
 * BEFORE they reach the underlying tool execution.
 */
@Slf4j
public class PiiToolCallbackWrapper implements ToolCallback {

    private final ToolCallback delegate;
    private final PiiService piiService;
    private final ObjectMapper objectMapper;

    public PiiToolCallbackWrapper(ToolCallback delegate, PiiService piiService) {
        this.delegate = delegate;
        this.piiService = piiService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        try {
            // 1. Parse Input JSON -> flexible type (Map, List, String, etc.)
            Object parsed = objectMapper.readValue(toolInput, Object.class);
            log.info("[FILTER] Intercepted Tool Call: '{}'. Input: {}", getToolDefinition().name(), parsed);

            // 2. Detokenize based on input structure
            Object cleanArgs = piiService.detokenizeRec(parsed);

            if (!parsed.equals(cleanArgs)) {
                log.info("[FILTER] Detokenized Arguments: {} -> {}", parsed, cleanArgs);
            }

            // 3. Serialize back to JSON for the delegate
            String cleanInput = objectMapper.writeValueAsString(cleanArgs);

            // 4. Call Delegate
            String result = delegate.call(cleanInput);
            
            // 5. Tokenize tool output (mask PII in results too!)
            String maskedResult = piiService.tokenize(result);
            if (!result.equals(maskedResult)) {
                log.info("[FILTER] Masked Tool Output: {} -> {}", result, maskedResult);
            }
            
            return maskedResult;

        } catch (JsonProcessingException e) {
            // Fallback: treat as plain string and detokenize directly
            log.warn("[FILTER] Input is not valid JSON, treating as plain string: {}", toolInput);
            String cleanInput = piiService.detokenize(toolInput);
            String result = delegate.call(cleanInput);
            return piiService.tokenize(result);
        }
    }
}
