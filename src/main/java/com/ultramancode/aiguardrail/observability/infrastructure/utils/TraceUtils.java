package com.ultramancode.aiguardrail.observability.infrastructure.utils;

import com.ultramancode.aiguardrail.common.observability.ObservabilityConstants;
import io.opentelemetry.api.trace.Span;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TraceUtils {

    private static final String INVALID_TRACE_ID = "00000000000000000000000000000000";

    public static boolean isValid(String traceId) {
        return traceId != null && !traceId.isEmpty() && !traceId.equals(INVALID_TRACE_ID);
    }

    /**
     * Tags the current span with Input for Langfuse OTel ingestion.
     */
    public static void tagSpanInput(String input) {
        if (input == null) return;
        Span span = Span.current();
        span.setAttribute(ObservabilityConstants.TAG_GEN_AI_PROMPT, input);
        span.setAttribute(ObservabilityConstants.TAG_INPUT_VALUE, input);
        span.setAttribute(ObservabilityConstants.TAG_INPUT, input); // Standard Key for UI
    }

    /**
     * Tags the current span with Output for Langfuse OTel ingestion.
     */
    public static void tagSpanOutput(String output) {
        if (output == null) return;
        Span span = Span.current();
        span.setAttribute(ObservabilityConstants.TAG_GEN_AI_COMPLETION, output);
        span.setAttribute(ObservabilityConstants.TAG_OUTPUT_VALUE, output);
        span.setAttribute(ObservabilityConstants.TAG_OUTPUT, output); // Standard Key for UI
    }
}
