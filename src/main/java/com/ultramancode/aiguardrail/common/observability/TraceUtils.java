package com.ultramancode.aiguardrail.common.observability;

import io.opentelemetry.api.trace.Span;
import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class TraceUtils {

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final String INVALID_TRACE_ID = "00000000000000000000000000000000";
    private static final String TAG_GEN_AI_PROMPT = "gen_ai.prompt";
    private static final String TAG_GEN_AI_COMPLETION = "gen_ai.completion";
    private static final String TAG_INPUT_VALUE = "input.value";
    private static final String TAG_OUTPUT_VALUE = "output.value";

    public static boolean isValid(String traceId) {
        if (traceId == null) {
            return false;
        }

        String normalizedTraceId = traceId.trim();
        if (normalizedTraceId.isEmpty()) {
            return false;
        }
        if (!TRACE_ID_PATTERN.matcher(normalizedTraceId).matches()) {
            return false;
        }
        if (INVALID_TRACE_ID.equals(normalizedTraceId)) {
            return false;
        }
        return true;
    }

    public static void tagSpanInput(String input) {
        if (input == null) {
            return;
        }
        Span span = Span.current();
        span.setAttribute(TAG_GEN_AI_PROMPT, input);
        span.setAttribute(TAG_INPUT_VALUE, input);
        span.setAttribute(ObservabilityTags.KEY_INPUT, input);
    }

    public static void tagSpanOutput(String output) {
        if (output == null) {
            return;
        }
        Span span = Span.current();
        span.setAttribute(TAG_GEN_AI_COMPLETION, output);
        span.setAttribute(TAG_OUTPUT_VALUE, output);
        span.setAttribute(ObservabilityTags.KEY_OUTPUT, output);
    }
}
