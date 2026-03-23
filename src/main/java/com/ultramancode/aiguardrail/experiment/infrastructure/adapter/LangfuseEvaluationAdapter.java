package com.ultramancode.aiguardrail.experiment.infrastructure.adapter;

import com.ultramancode.aiguardrail.common.integration.langfuse.client.LangfuseScoreClient;
import com.ultramancode.aiguardrail.common.observability.LangfuseConstants;
import com.ultramancode.aiguardrail.common.observability.command.RecordScoreCommand;
import com.ultramancode.aiguardrail.common.observability.TraceUtils;
import com.ultramancode.aiguardrail.common.util.PositiveConfigResolver;
import com.ultramancode.aiguardrail.experiment.application.port.out.EvaluationRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Langfuse 데이터셋/점수 관리 API를 연결하는 어댑터입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseEvaluationAdapter implements EvaluationRepositoryPort {

    private static final int DEFAULT_DATASET_RUN_ITEMS_LIMIT = 200;
    private static final int DEFAULT_MAX_DATASET_RUN_ITEM_PAGES = 5000;
    private static final int DEFAULT_DATASET_ITEMS_LIMIT = 100;
    private static final int DEFAULT_MAX_DATASET_ITEM_PAGES = 5000;

    private final LangfuseScoreClient scoreClient;
    private final LangfuseScoreQuerySupport scoreQuerySupport;
    private final LangfuseDatasetQuerySupport datasetQuerySupport;

    @Qualifier("langfuseWebClient")
    private final WebClient client;

    @Value("${langfuse.link.fail-open:false}")
    private boolean linkFailOpen;

    @Value("${experiment.langfuse.dataset-run-items-limit:" + DEFAULT_DATASET_RUN_ITEMS_LIMIT + "}")
    private int configuredDatasetRunItemsLimit;

    @Value("${experiment.langfuse.max-dataset-run-item-pages:" + DEFAULT_MAX_DATASET_RUN_ITEM_PAGES + "}")
    private int configuredMaxDatasetRunItemPages;

    @Value("${experiment.langfuse.dataset-items-limit:" + DEFAULT_DATASET_ITEMS_LIMIT + "}")
    private int configuredDatasetItemsLimit;

    @Value("${experiment.langfuse.max-dataset-item-pages:" + DEFAULT_MAX_DATASET_ITEM_PAGES + "}")
    private int configuredMaxDatasetItemPages;

    /**
     * Langfuse에서 점수 목록을 조회합니다.
     */
    @Override
    public ScorePageResult fetchScores(int page, int limit) {
        try {
            return scoreQuerySupport.fetchScoresPage(page, limit);
        } catch (IllegalStateException e) {
            log.error("[EXPERIMENT] Failed to fetch scores: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to fetch scores from Langfuse", e);
        }
    }

    @Override
    public Set<String> fetchTraceIdsByRunName(String runName) {
        return datasetQuerySupport.fetchTraceIdsByRunName(
                runName,
                resolveDatasetRunItemsLimit(),
                resolveMaxDatasetRunItemPages()
        );
    }

    /**
     * Langfuse SDK 경로로 점수를 기록합니다.
     */
    @Override
    public void recordScore(RecordScoreCommand command) {
        scoreClient.recordScore(command);
    }

    /**
     * 데이터셋 아이템 목록을 조회합니다.
     */
    @Override
    public List<Map<String, Object>> fetchDatasetItems(String datasetName) {
        return datasetQuerySupport.fetchDatasetItems(
                datasetName,
                resolveDatasetItemsPageSize(),
                resolveMaxDatasetItemPages()
        );
    }

    @Override
    public List<Map<String, Object>> fetchDatasetItemsPage(String datasetName, int page, int limit) {
        return datasetQuerySupport.fetchDatasetItemsPage(datasetName, page, limit);
    }

    @Override
    public int resolveDatasetItemsPageSize() {
        return resolvePositiveValue(
                configuredDatasetItemsLimit,
                DEFAULT_DATASET_ITEMS_LIMIT,
                "experiment.langfuse.dataset-items-limit"
        );
    }

    @Override
    public int countDatasetItems(String datasetName) {
        return datasetQuerySupport.countDatasetItems(
                datasetName,
                resolveDatasetItemsPageSize(),
                resolveMaxDatasetItemPages()
        );
    }

    /**
     * 데이터셋 아이템과 실행 결과를 연결합니다.
     */
    @Override
    public void linkDatasetRunItem(String runName, String datasetItemId, String traceId, @Nullable String observationId) {
        String validationError = validateLinkArguments(runName, datasetItemId, traceId);
        if (validationError != null) {
            if (linkFailOpen) {
                log.warn("[EXPERIMENT] Skip dataset-run-item link by policy. reason={}", validationError);
                return;
            }
            throw new IllegalArgumentException(validationError);
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put(LangfuseConstants.KEY_RUN_NAME, runName);
            body.put(LangfuseConstants.KEY_DATASET_ITEM_ID, datasetItemId);
            body.put(LangfuseConstants.KEY_TRACE_ID, traceId);

            if (observationId != null) {
                body.put("observationId", observationId);
            }

            client.post()
                    .uri("/api/public/dataset-run-items")
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (RuntimeException e) {
            if (linkFailOpen) {
                log.warn(
                        "[EXPERIMENT] Failed to link dataset item but allowed by policy. runName={}, itemId={}, cause={}",
                        runName,
                        datasetItemId,
                        e.getMessage(),
                        e
                );
                return;
            }
            throw new IllegalStateException("Failed to link dataset-run item to Langfuse", e);
        }
    }

    private String validateLinkArguments(String runName, String datasetItemId, String traceId) {
        if (runName == null || runName.isBlank()) {
            return "runName must not be blank";
        }

        if (datasetItemId == null || datasetItemId.isBlank()) {
            return "datasetItemId must not be blank";
        }

        if (!TraceUtils.isValid(traceId)) {
            return "traceId must be a valid non-empty value";
        }

        return null;
    }

    private int resolvePositiveValue(int configuredValue, int fallback, String propertyName) {
        return PositiveConfigResolver.resolve(
                configuredValue,
                fallback,
                "[EXPERIMENT]",
                propertyName
        );
    }

    private int resolveDatasetRunItemsLimit() {
        return resolvePositiveValue(
                configuredDatasetRunItemsLimit,
                DEFAULT_DATASET_RUN_ITEMS_LIMIT,
                "experiment.langfuse.dataset-run-items-limit"
        );
    }

    private int resolveMaxDatasetRunItemPages() {
        return resolvePositiveValue(
                configuredMaxDatasetRunItemPages,
                DEFAULT_MAX_DATASET_RUN_ITEM_PAGES,
                "experiment.langfuse.max-dataset-run-item-pages"
        );
    }

    private int resolveMaxDatasetItemPages() {
        return resolvePositiveValue(
                configuredMaxDatasetItemPages,
                DEFAULT_MAX_DATASET_ITEM_PAGES,
                "experiment.langfuse.max-dataset-item-pages"
        );
    }
}
