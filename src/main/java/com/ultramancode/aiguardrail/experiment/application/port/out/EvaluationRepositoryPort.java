package com.ultramancode.aiguardrail.experiment.application.port.out;

import com.ultramancode.aiguardrail.common.observability.RecordScoreCommand;
import com.ultramancode.aiguardrail.experiment.application.result.ScoreResult;
import com.ultramancode.aiguardrail.guardrail.application.domain.FetchedPrompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port for Evaluation/Observability Backend (e.g. Langfuse).
 * Decouples Application Layer from specific MLOps tools.
 */
public interface EvaluationRepositoryPort {

    /**
     * Fetch dataset items for experimentation.
     */
    List<Map<String, Object>> fetchDatasetItems(String datasetName);

    /**
     * Link a specific test run trace to a dataset item.
     */
    void linkDatasetRunItem(String runName, String datasetItemId, String traceId, String observationId);

    /**
     * Fetch system prompt version (with metadata).
     */
    Optional<FetchedPrompt> fetchPrompt(String promptName);

    /**
     * Record an evaluation score.
     */
    void recordScore(RecordScoreCommand command);

    /**
     * Fetch recorded scores for aggregation.
     */
    List<ScoreResult> fetchScores(int page, int limit);
}
