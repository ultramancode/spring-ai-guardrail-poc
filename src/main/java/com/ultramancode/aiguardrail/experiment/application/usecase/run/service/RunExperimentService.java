package com.ultramancode.aiguardrail.experiment.application.usecase.run.service;

import com.ultramancode.aiguardrail.common.observability.ObservabilityTags;
import com.ultramancode.aiguardrail.common.util.ErrorMessageResolver;
import com.ultramancode.aiguardrail.common.util.PageSignatureUtils;
import com.ultramancode.aiguardrail.common.util.PositiveConfigResolver;
import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.application.port.in.RunExperimentUseCase;
import com.ultramancode.aiguardrail.experiment.application.port.out.EvaluationRepositoryPort;
import com.ultramancode.aiguardrail.experiment.application.result.ExperimentResult;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.assembler.ExperimentResultAssembler;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.model.ExperimentRunMetrics;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.support.ExperimentPromptSupport;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.support.dataset.DatasetItemSignatureResolver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunExperimentService implements RunExperimentUseCase {

    private static final int DEFAULT_PROMPT_FAILURE_DETAIL_LIMIT = 50;
    private static final int DEFAULT_MAX_RESPONSE_DETAILS = 500;
    private static final int DEFAULT_MAX_DATASET_PAGES = 5000;

    private final EvaluationRepositoryPort evaluationPort;
    private final DatasetItemSignatureResolver datasetItemSignatureResolver;
    private final ExperimentResultAssembler resultAssembler;
    private final ExperimentEvaluator experimentEvaluator;
    private final ObservationRegistry observationRegistry;
    private final ExperimentPromptSupport promptSupport;
    private final ExperimentCaseWorkflowService caseWorkflowService;

    @Value("${experiment.prompt-resolution-failure.max-detail-items:" + DEFAULT_PROMPT_FAILURE_DETAIL_LIMIT + "}")
    private int promptResolutionFailureMaxDetailItems;

    @Value("${experiment.max-dataset-pages:" + DEFAULT_MAX_DATASET_PAGES + "}")
    private int configuredMaxDatasetPages;

    @Value("${experiment.response.max-details:" + DEFAULT_MAX_RESPONSE_DETAILS + "}")
    private int configuredMaxResponseDetails;

    @Override
    public ExperimentResult runExperiment(RunExperimentCommand command) {
        long startTime = System.currentTimeMillis();
        normalizeAndValidateCommand(command);
        ExperimentPromptSupport.ResolvedPrompt promptResolution = promptSupport.resolvePromptOnce(command.getPrompt());
        int datasetItemPageSize = evaluationPort.resolveDatasetItemsPageSize();

        Observation rootObs = observationRegistry.getCurrentObservation();
        if (rootObs != null) {
            rootObs.highCardinalityKeyValue(
                    ObservabilityTags.KEY_INPUT,
                    String.format("Experiment: %s\nDataset: %s", command.getRunName(), command.getDatasetName())
            );
        }

        if (promptResolution.errorMessage() != null) {
            return handlePromptResolutionFailure(
                    command,
                    startTime,
                    promptResolution.errorMessage(),
                    datasetItemPageSize,
                    rootObs
            );
        }

        int maxResponseDetails = resolveMaxResponseDetails();
        ExperimentRunMetrics runMetrics = new ExperimentRunMetrics(maxResponseDetails);
        int page = 1;
        int maxDatasetPages = resolveMaxDatasetPages();
        String previousPageSignature = null;
        boolean partialResult = false;

        while (true) {
            if (page > maxDatasetPages) {
                throw new IllegalStateException("Exceeded max dataset pages: " + maxDatasetPages);
            }

            List<Map<String, Object>> pageItems =
                    evaluationPort.fetchDatasetItemsPage(command.getDatasetName(), page, datasetItemPageSize);
            if (pageItems.isEmpty()) {
                break;
            }

            String currentPageSignature = PageSignatureUtils.buildPageSignature(
                    pageItems,
                    datasetItemSignatureResolver::resolve
            );
            if (currentPageSignature != null && currentPageSignature.equals(previousPageSignature)) {
                log.warn(
                        "[EXPERIMENT] Detected repeated dataset page. stop paging. runName={}, page={}",
                        command.getRunName(),
                        page
                );
                partialResult = true;
                break;
            }
            previousPageSignature = currentPageSignature;

            log.info(
                    "[EXPERIMENT] Fetched {} items for run: {} (page={}, size={})",
                    pageItems.size(),
                    command.getRunName(),
                    page,
                    datasetItemPageSize
            );

            int pageStartItemIndex = runMetrics.getTotalCount();
            for (int i = 0; i < pageItems.size(); i++) {
                Map<String, Object> item = pageItems.get(i);
                int itemIndex = pageStartItemIndex + i;
                ExperimentResult.TestCaseResult detail = caseWorkflowService.processTestCase(
                        command,
                        item,
                        itemIndex,
                        promptResolution.systemPrompt()
                );
                runMetrics.append(detail, experimentEvaluator::isUnsafe);
            }

            if (pageItems.size() < datasetItemPageSize) {
                break;
            }
            page++;
        }

        return resultAssembler.buildFinalResult(
                command,
                startTime,
                runMetrics.getTotalCount(),
                runMetrics.getPassedCount(),
                runMetrics.getErrorCount(),
                runMetrics.getRecordingErrorCount(),
                runMetrics.getTruePositive(),
                runMetrics.getTrueNegative(),
                runMetrics.getFalsePositive(),
                runMetrics.getFalseNegative(),
                runMetrics.averageReasonScoreOrNull(),
                runMetrics.isDetailsSampled(),
                runMetrics.getDetails(),
                partialResult,
                rootObs
        );
    }

    private int resolveMaxDatasetPages() {
        return PositiveConfigResolver.resolve(
                configuredMaxDatasetPages,
                DEFAULT_MAX_DATASET_PAGES,
                "[EXPERIMENT]",
                "experiment.max-dataset-pages"
        );
    }

    private int resolveMaxResponseDetails() {
        return PositiveConfigResolver.resolve(
                configuredMaxResponseDetails,
                DEFAULT_MAX_RESPONSE_DETAILS,
                "[EXPERIMENT]",
                "experiment.response.max-details"
        );
    }

    private ExperimentResult handlePromptResolutionFailure(
            RunExperimentCommand command,
            long startTime,
            String errorMessage,
            int datasetItemPageSize,
            Observation rootObservation
    ) {
        int detailLimit = resolvePromptResolutionFailureDetailLimit();
        List<Map<String, Object>> sampledItems = fetchPromptFailureSampleItems(
                command.getDatasetName(),
                datasetItemPageSize,
                detailLimit
        );
        int totalItemCount = resolvePromptFailureTotalCount(command.getDatasetName(), sampledItems.size());
        log.error(
                "[EXPERIMENT] Aborting run due to system prompt resolution failure. totalItems={}, sampledItems={}, detailLimit={}, error={}",
                totalItemCount,
                sampledItems.size(),
                detailLimit,
                errorMessage
        );
        return resultAssembler.buildPromptResolutionFailureResult(
                command,
                startTime,
                totalItemCount,
                sampledItems,
                errorMessage,
                rootObservation
        );
    }

    private int resolvePromptResolutionFailureDetailLimit() {
        if (promptResolutionFailureMaxDetailItems <= 0) {
            log.warn(
                    "[EXPERIMENT] Invalid prompt failure detail limit: {}. Fallback to {}.",
                    promptResolutionFailureMaxDetailItems,
                    DEFAULT_PROMPT_FAILURE_DETAIL_LIMIT
            );
            return DEFAULT_PROMPT_FAILURE_DETAIL_LIMIT;
        }
        return promptResolutionFailureMaxDetailItems;
    }

    private List<Map<String, Object>> fetchPromptFailureSampleItems(
            String datasetName,
            int datasetItemPageSize,
            int detailLimit
    ) {
        int fetchSize = datasetItemPageSize;
        if (fetchSize > detailLimit) {
            fetchSize = detailLimit;
        }
        if (fetchSize <= 0) {
            return List.of();
        }

        try {
            return evaluationPort.fetchDatasetItemsPage(datasetName, 1, fetchSize);
        } catch (RuntimeException e) {
            log.warn(
                    "[EXPERIMENT] Failed to fetch sample dataset items for prompt failure details. dataset={}, cause={}",
                    datasetName,
                    ErrorMessageResolver.resolve(e, "RuntimeException"),
                    e
            );
            return List.of();
        }
    }

    private int resolvePromptFailureTotalCount(String datasetName, int sampledItemCount) {
        try {
            return evaluationPort.countDatasetItems(datasetName);
        } catch (RuntimeException e) {
            log.warn(
                    "[EXPERIMENT] Failed to resolve full dataset count for prompt failure. dataset={}, fallbackSampledCount={}, cause={}",
                    datasetName,
                    sampledItemCount,
                    ErrorMessageResolver.resolve(e, "RuntimeException"),
                    e
            );
            return sampledItemCount;
        }
    }

    private void normalizeAndValidateCommand(RunExperimentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Run experiment command must not be null.");
        }
        command.normalizeAndValidateOrThrow();
    }
}

