package com.ultramancode.aiguardrail.infrastructure.guardrail;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Structured output for Guard LLM safety classification.
 * Uses Spring AI's structured output feature to ensure type-safe responses.
 *
 * <p>Example JSON response from LLM:</p>
 * <pre>
 * {
 *   "verdict": "SAFE",
 *   "reason": "Normal user request with no malicious intent"
 * }
 * </pre>
 */
public record SafetyVerdict(
    @JsonProperty(required = true)
    @JsonPropertyDescription("Safety classification result: SAFE or UNSAFE")
    Verdict verdict,
    
    @JsonProperty
    @JsonPropertyDescription("Brief explanation for the classification decision")
    String reason
) {
    /**
     * Enum for safety classification verdict.
     * Using enum ensures only valid values are accepted.
     */
    public enum Verdict {
        SAFE,
        UNSAFE
    }
    
    /**
     * Convenience method to check if the verdict is unsafe.
     */
    public boolean isUnsafe() {
        return verdict == Verdict.UNSAFE;
    }
    
    /**
     * Convenience method to check if the verdict is safe.
     */
    public boolean isSafe() {
        return verdict == Verdict.SAFE;
    }
}
