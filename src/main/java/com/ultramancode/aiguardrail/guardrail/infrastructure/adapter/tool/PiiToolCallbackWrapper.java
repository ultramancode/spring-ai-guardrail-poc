package com.ultramancode.aiguardrail.guardrail.infrastructure.adapter.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultramancode.aiguardrail.common.observability.ObservabilityConstants;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.Observation;

/**
 * Wraps an existing ToolCallback to transparently detokenize arguments
 * BEFORE they reach the underlying tool execution.
 */
@Slf4j
public class PiiToolCallbackWrapper implements ToolCallback {

    private final ToolCallback delegate;
    private final PiiUseCase piiService;
    private final ObjectMapper objectMapper;
    private final ObservationRegistry observationRegistry;
    private final boolean auditMode;

    public PiiToolCallbackWrapper(ToolCallback delegate, PiiUseCase piiService, ObservationRegistry observationRegistry,
                                  boolean auditMode) {
        this.delegate = delegate;
        this.piiService = piiService;
        this.observationRegistry = observationRegistry;
        this.objectMapper = new ObjectMapper();
        this.auditMode = auditMode;
    }

    public PiiToolCallbackWrapper(ToolCallback delegate, PiiUseCase piiService,
                                  ObservationRegistry observationRegistry) {
        this(delegate, piiService, observationRegistry, false);
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        // [Observation] Create a named span for the specific tool (e.g., tool_call
        // search-address)
        // This ensures visibility in Langfuse while we use Internal methods to keep it
        // clean.
        Observation obs = Observation.createNotStarted(getToolDefinition().name(), observationRegistry)
                .lowCardinalityKeyValue(ObservabilityConstants.LF_OBSERVATION_TYPE,
                        ObservabilityConstants.LF_VAL_TOOL);

        return obs.observe(() -> {
            try {
                // 1. Parse Input JSON -> flexible type (Map, List, String, etc.)
                Object parsed = objectMapper.readValue(toolInput, Object.class);
                log.info("[FILTER] Intercepted Tool Call: '{}'. Input: {}", getToolDefinition().name(), parsed);

                // 2. Detokenize based on input structure
                Object cleanArgs = piiService.detokenizeRec(parsed);

                recordInputAudit(parsed, cleanArgs);

                // 3. Serialize back to JSON for the delegate
                String cleanInput = objectMapper.writeValueAsString(cleanArgs);

                // 4. Call Delegate
                String result = delegate.call(cleanInput);

                // 5. Tokenize tool output (mask PII in results too!)
                // Use tokenizeInternal to avoid creating extra spans inside the tool span
                String maskedResult = piiService.tokenizeInternal(result);

                recordOutputAudit(result, maskedResult);

                return maskedResult;

            } catch (JsonProcessingException e) {
                obs.error(e);
                log.warn("[FILTER] Input is not valid JSON, treating as plain string: {}", toolInput);
                String cleanInput = piiService.detokenizeInternal(toolInput);
                String result = delegate.call(cleanInput);
                return piiService.tokenizeInternal(result);
            }
        });
    }

    private void recordInputAudit(Object original, Object cleaned) {
        if (!auditMode)
            return;
        Span span = Span.current();
        span.setAttribute(ObservabilityConstants.TAG_AUDIT_DECRYPTED_ARGS, cleaned.toString());
        log.warn("[AUDIT] Decrypted arguments recorded in trace: {}", cleaned);

        if (!original.equals(cleaned)) {
            log.info("[FILTER] Detokenized Arguments: {} -> {}", original, cleaned);
            span.setAttribute(ObservabilityConstants.TAG_PII_DETOKENIZED, true);
        }
    }

    private void recordOutputAudit(String original, String masked) {
        if (!auditMode)
            return;
        Span span = Span.current();
        span.setAttribute(ObservabilityConstants.TAG_AUDIT_DECRYPTED_RESULT, original);

        if (!original.equals(masked)) {
            log.info("[FILTER] Masked Tool Output: {} -> {}", original, masked);
            span.setAttribute(ObservabilityConstants.TAG_TOOL_RESULT_MASKED, true);
        }
    }
}

