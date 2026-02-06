package com.ultramancode.aiguardrail.guardrail.application.port.out;

import com.ultramancode.aiguardrail.common.observability.RecordScoreCommand;
import com.ultramancode.aiguardrail.guardrail.application.domain.FetchedPrompt;
import com.ultramancode.aiguardrail.multimodal.domain.GenerationAttachment;
import com.ultramancode.aiguardrail.common.observability.UsageResult;

import java.util.Optional;

/**
 * Port for Guardrail Observability (Prompt fetching, Score recording).
 */
public interface GuardrailObservabilityPort {

    /**
     * Fetch system prompt version (with metadata).
     */
    Optional<FetchedPrompt> fetchPrompt(String promptName);

    /**
     * Record an evaluation score.
     */
    void recordScore(RecordScoreCommand command);

    void traceInput(String input);

    void traceOutput(String output);

    boolean isValidTraceId(String traceId);


    /**
     * LLM 생성 작업 기록 (Unified)
     */
    void recordGeneration(String traceId, String name, String modelName,
                          String input, String output,
                          GenerationAttachment attachment,
                          UsageResult usage,
                          long startTime, long endTime);
}
