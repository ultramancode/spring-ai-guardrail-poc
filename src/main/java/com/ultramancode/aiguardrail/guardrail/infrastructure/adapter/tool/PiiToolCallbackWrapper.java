package com.ultramancode.aiguardrail.guardrail.infrastructure.adapter.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultramancode.aiguardrail.common.observability.ObservabilityConstants;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Wraps an existing ToolCallback to transparently detokenize arguments
 * BEFORE they reach the underlying tool execution.
 */
@Slf4j
public class PiiToolCallbackWrapper implements ToolCallback {

    private final ToolCallback delegate;
    private final PiiUseCase piiService;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final boolean auditMode;

    public PiiToolCallbackWrapper(ToolCallback delegate, PiiUseCase piiService, boolean auditMode) {
        this.delegate = delegate;
        this.piiService = piiService;
        this.objectMapper = new ObjectMapper();
        this.auditMode = auditMode;
        // Use global tracer for simplicity in wrapper
        this.tracer = GlobalOpenTelemetry.getTracer("com.ultramancode.aiguardrail");
    }

    public PiiToolCallbackWrapper(ToolCallback delegate, PiiUseCase piiService) {
        this(delegate, piiService, false);
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        Span span = Span.current(); // Attach to existing span (Spring AI's tool span)

        // [Observation] Mark this as a Tool Call for correct visualization in Langfuse
        span.setAttribute(ObservabilityConstants.LF_OBSERVATION_TYPE, ObservabilityConstants.LF_VAL_TOOL);

        try {
            // 1. Parse Input JSON -> flexible type (Map, List, String, etc.)
            Object parsed = objectMapper.readValue(toolInput, Object.class);
            log.info("[FILTER] Intercepted Tool Call: '{}'. Input: {}", getToolDefinition().name(), parsed);

            // 2. Detokenize based on input structure
            Object cleanArgs = piiService.detokenizeRec(parsed);

            recordInputAudit(span, parsed, cleanArgs);

            // 3. Serialize back to JSON for the delegate
            String cleanInput = objectMapper.writeValueAsString(cleanArgs);

            // 4. Call Delegate
            String result = delegate.call(cleanInput);

            // 5. Tokenize tool output (mask PII in results too!)
            String maskedResult = piiService.tokenize(result);

            recordOutputAudit(span, result, maskedResult);

            return maskedResult;

        } catch (JsonProcessingException e) {
            span.recordException(e);
            log.warn("[FILTER] Input is not valid JSON, treating as plain string: {}", toolInput);
            String cleanInput = piiService.detokenize(toolInput);
            String result = delegate.call(cleanInput);
            return piiService.tokenize(result);
        }
    }

    private void recordInputAudit(Span span, Object original, Object detokenized) {
        if (this.auditMode) {
            span.setAttribute(ObservabilityConstants.TAG_AUDIT_DECRYPTED_ARGS, detokenized.toString());
            log.warn("[AUDIT] Decrypted arguments recorded in trace: {}", detokenized);
        }

        if (!original.equals(detokenized)) {
            log.info("[FILTER] Detokenized Arguments: {} -> {}", original, detokenized);
            span.setAttribute(ObservabilityConstants.TAG_PII_DETOKENIZED, true);
        }
    }

    private void recordOutputAudit(Span span, String originalResult, String maskedResult) {
        if (this.auditMode) {
            span.setAttribute(ObservabilityConstants.TAG_AUDIT_DECRYPTED_RESULT, originalResult);
        }

        if (!originalResult.equals(maskedResult)) {
            log.info("[FILTER] Masked Tool Output: {} -> {}", originalResult, maskedResult);
            span.setAttribute(ObservabilityConstants.TAG_TOOL_RESULT_MASKED, true);
        }
    }
}

