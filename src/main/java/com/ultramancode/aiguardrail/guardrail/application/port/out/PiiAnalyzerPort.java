package com.ultramancode.aiguardrail.guardrail.application.port.out;

import com.ultramancode.aiguardrail.guardrail.domain.PiiSpan;

import java.util.List;

/**
 * Port for PII Analysis Engines.
 * Decouples Application Layer (Service) from Infrastructure Layer (Phileas, Presidio).
 */
public interface PiiAnalyzerPort {

    /**
     * Analyze text and return detected PII spans.
     *
     * @param text Input text
     * @return List of detected PII spans
     */
    List<PiiSpan> analyze(String text);
}
