package com.ultramancode.aiguardrail.observability.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langfuse.client.LangfuseClient;
import com.langfuse.client.resources.commons.types.CreateScoreValue;
import com.langfuse.client.resources.score.types.CreateScoreRequest;
import com.ultramancode.aiguardrail.common.observability.ObservabilityConstants;
import com.ultramancode.aiguardrail.common.observability.RecordScoreCommand;
import com.ultramancode.aiguardrail.experiment.application.port.out.EvaluationRepositoryPort;
import com.ultramancode.aiguardrail.experiment.application.result.ScoreResult;
import com.ultramancode.aiguardrail.guardrail.application.domain.FetchedPrompt;
import com.ultramancode.aiguardrail.observability.infrastructure.utils.TraceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * Langfuse Management Adapter (Low Volume)
 * <p>
 * [책임]
 * - 점수(Score) 조회 및 기록 (Read/Write)
 * - 데이터셋(Dataset) 조회 및 연결
 * - 프롬프트(Prompt) 조회
 * - Application DTO (ScoreResult) 반환 (Presentation DTO 의존 없음)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseEvaluationAdapter implements EvaluationRepositoryPort {

    private final LangfuseClient langfuse; // Official SDK (for simple ops)
    @Qualifier("langfuseWebClient")
    private final WebClient client;      // Custom REST Client (for missing features)
    private final ObjectMapper objectMapper;

    @Value("${langfuse.prompt-label:production}")
    private String promptLabel;

    /**
     * Fetch raw scores from Langfuse API
     */
    @Override
    public List<ScoreResult> fetchScores(int page, int limit) {
        try {
            String response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/public/scores")
                            .queryParam("page", page)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");

            if (data == null || !data.isArray()) return Collections.emptyList();

            List<ScoreResult> records = new ArrayList<>();
            for (JsonNode node : data) {
                // Map JSON to Application DTO
                records.add(ScoreResult.builder()
                        .id(node.path(ObservabilityConstants.LF_ID).asText())
                        .traceId(node.path(ObservabilityConstants.LF_TRACE_ID).asText())
                        .name(node.path(ObservabilityConstants.LF_NAME).asText())
                        .value(node.path(ObservabilityConstants.LF_VALUE).asDouble())
                        .comment(node.path(ObservabilityConstants.LF_COMMENT).asText())
                        .build());

            }
            return records;

        } catch (Exception e) {
            log.error("[MANAGEMENT] Failed to fetch scores: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Record a score (using SDK)
     */
    @Override
    public void recordScore(RecordScoreCommand command) {
        if (!TraceUtils.isValid(command.traceId())) {
            return;
        }

        try {
            langfuse.score().create(CreateScoreRequest.builder()
                    .name(command.scoreName())
                    .value(CreateScoreValue.of(command.value()))
                    .traceId(command.traceId())
                    .comment(command.comment())
                    .observationId(command.observationId())
                    .build());
            log.debug("[MANAGEMENT] Recorded score: {} = {}", command.scoreName(), command.value());
        } catch (Exception e) {
            log.warn("[MANAGEMENT] Failed to record score: {}", e.getMessage());
        }
    }

    /**
     * Fetch Dataset Items
     */
    @Override
    public List<Map<String, Object>> fetchDatasetItems(String datasetName) {
        try {
            String response = client.get()
                    .uri("/api/public/dataset-items?datasetName=" + datasetName)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");

            if (data == null || !data.isArray()) return Collections.emptyList();

            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode node : data) {
                items.add(objectMapper.convertValue(node, new TypeReference<>() {
                }));
            }
            return items;

        } catch (Exception e) {
            log.error("[MANAGEMENT] Failed to fetch dataset: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Link Item to Run
     */
    @Override
    public void linkDatasetRunItem(String runName, String datasetItemId, String traceId, String observationId) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put(ObservabilityConstants.LF_RUN_NAME, runName);
            body.put(ObservabilityConstants.LF_DATASET_ITEM_ID, datasetItemId);
            body.put(ObservabilityConstants.LF_TRACE_ID, traceId);

            if (observationId != null) {
                body.put("observationId", observationId);
            }

            client.post()
                    .uri("/api/public/dataset-run-items")
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.warn("[MANAGEMENT] Failed to link dataset item: {}", e.getMessage());
        }
    }

    /**
     * Fetch Prompt
     */
    @Override
    public Optional<FetchedPrompt> fetchPrompt(String promptName) {
        try {
            // SDK 0.1.2: prompts().get(name) defaults to production/latest
            var prompt = langfuse.prompts().get(promptName);

            // Extract text from TextPrompt
            if (prompt.isText() && prompt.getText().isPresent()) {
                var textPrompt = prompt.getText().get();
                log.info("[MANAGEMENT] Successfully fetched prompt '{}' from Langfuse (v{})", promptName, textPrompt.getVersion());
                return Optional.of(new FetchedPrompt(
                        textPrompt.getPrompt().stripIndent(),
                        promptName,
                        textPrompt.getVersion()
                ));
            }
            // Handle ChatPrompt if needed (omitted for now as we return String)

        } catch (Exception e) {
            log.warn("[MANAGEMENT] Failed to fetch prompt: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
