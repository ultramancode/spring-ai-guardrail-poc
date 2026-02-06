package com.ultramancode.aiguardrail.guardrail.domain;

/**
 * Represents a detected PII span in the text.
 * used for communication between Infrastructure Scanners and Application Service.
 */
public record PiiSpan(
        String type,
        int start,
        int end,
        String text,
        String source,
        double score
) {
}
