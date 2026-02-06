package com.ultramancode.aiguardrail.experiment.application.service;

import com.google.common.base.CaseFormat;
import com.ultramancode.aiguardrail.common.util.MediaTokenUtils;
import com.ultramancode.aiguardrail.common.observability.ObservabilityConstants;
import com.ultramancode.aiguardrail.common.observability.RecordScoreCommand;
import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.application.port.out.EvaluationRepositoryPort;

import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;
import com.ultramancode.aiguardrail.experiment.application.result.ExperimentResult;
import com.ultramancode.aiguardrail.experiment.domain.EvaluationMethod;
import com.ultramancode.aiguardrail.experiment.application.command.EvaluationCommand;
import com.ultramancode.aiguardrail.multimodal.application.port.in.AnalyzeImageUseCase;
import com.ultramancode.aiguardrail.multimodal.application.port.in.AnalyzePdfUseCase;
import com.ultramancode.aiguardrail.experiment.application.port.in.EvaluationUseCase;
import com.ultramancode.aiguardrail.experiment.application.port.in.RunExperimentUseCase;
import com.ultramancode.aiguardrail.experiment.application.result.HumanEvaluationResult;
import com.ultramancode.aiguardrail.experiment.application.result.EvaluationMatchResult;
import com.ultramancode.aiguardrail.experiment.application.result.ScoreResult;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunExperimentService implements RunExperimentUseCase {

    private final EvaluationRepositoryPort evaluationPort;
    private final ExperimentTargetResolver targetResolver;
    private final ExperimentFileLoader fileLoader;
    private final AnalyzePdfUseCase analyzePdfUseCase;
    private final AnalyzeImageUseCase analyzeImageUseCase;
    private final EvaluationUseCase evaluationUseCase;
    private final ObservationRegistry observationRegistry;

    @Override
    public ExperimentResult runExperiment(RunExperimentCommand command) {
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> items = evaluationPort.fetchDatasetItems(command.getDatasetName());
        log.info("[EXPERIMENT] Fetched {} items from dataset: {}", items.size(), command.getDatasetName());

        // Enrich the Root (API-level) Observation to avoid 'undefined' in Langfuse
        Observation rootObs = observationRegistry.getCurrentObservation();
        if (rootObs != null) {
            rootObs.highCardinalityKeyValue(ObservabilityConstants.TAG_INPUT, String.format("Experiment: %s\nDataset: %s\nItems: %d",
                    command.getRunName(), command.getDatasetName(), items.size()));
        }

        List<ExperimentResult.TestCaseResult> details = new ArrayList<>();
        int passedCount = 0;

        for (Map<String, Object> item : items) {
            String itemId = (String) item.get("id");

            // 1. Extract input question and expected output
            String inputQuestion = extractField(item, command.getFieldMapping().getInput(), command.getFieldMapping().getInput());
            String expectedOutput = extractField(item, command.getFieldMapping().getExpected(), command.getFieldMapping().getExpected());

            // 2. Load file if metadata exists
            Map<String, Object> metadata = (Map<String, Object>) item.get("metadata");
            MultipartFile file = null;
            if (metadata != null) {
                file = fileLoader.loadFile(metadata);
            }
            final MultipartFile finalFile = file;

            // 3. Resolve target service and run within a Root Observation for full workflow tracing
            ExperimentResult.TestCaseResult detail = Observation.createNotStarted("multimodal-analysis-workflow", observationRegistry)
                    .lowCardinalityKeyValue(ObservabilityConstants.LF_OBSERVATION_TYPE, ObservabilityConstants.LF_VAL_CHAIN)
                    .highCardinalityKeyValue(ObservabilityConstants.TAG_EXP_RUN_NAME, command.getRunName())
                    .highCardinalityKeyValue(ObservabilityConstants.TAG_DATASET_ITEM_ID, itemId)
                    .highCardinalityKeyValue(ObservabilityConstants.TAG_CONTEXT_TYPE, "experiment")
                    .observe(() -> {
                        // Capture the actual OTEL trace ID and span ID
                        var spanContext = Span.current().getSpanContext();
                        String realTraceId = spanContext.getTraceId();
                        String realSpanId = spanContext.getSpanId();
                        log.debug("[EXPERIMENT] Current OTEL TraceID: {}, SpanID: {}", realTraceId, realSpanId);

                        Object actualResult = targetResolver.resolveAndExecute(command.getTarget(), inputQuestion, finalFile, realTraceId);
                        String actualResponse = "";
                        String maskedResponse = "";

                        if (actualResult instanceof MultimodalAnalysisResult) {
                            MultimodalAnalysisResult res = (MultimodalAnalysisResult) actualResult;
                            actualResponse = res.getAnswer();
                            maskedResponse = res.getMaskedAnswer();
                        } else if (actualResult != null) {
                            actualResponse = actualResult.toString();
                            maskedResponse = actualResponse;
                        }

                        // Update current observation with input/output for Langfuse UI mapping
                        // Prepend media token if present in metadata for rich preview
                        String inputDisplay = inputQuestion;
                        String mediaToken = extractMediaToken(metadata);
                        if (mediaToken != null) {
                            inputDisplay = mediaToken + "\n" + inputDisplay;
                        }

                        Observation currentObs = observationRegistry.getCurrentObservation();
                        if (currentObs != null) {
                            // Standard mapping for Langfuse UI
                            currentObs.highCardinalityKeyValue(ObservabilityConstants.TAG_INPUT, inputDisplay);
                            currentObs.highCardinalityKeyValue(ObservabilityConstants.TAG_OUTPUT, actualResponse);
                        }

                        // 1. Evaluate
                        var matchResult = EvaluationMatchResult.builder().match(false).score(0.0).build();
                        String evalTypeStr = command.getEvaluation().getType();
                        EvaluationMethod evalMethod = EvaluationMethod.fromValue(evalTypeStr);
                        double threshold = command.getEvaluation().getThreshold();

                        if (command.getEvaluation().isEvaluateMasked()) {
                            matchResult = evaluationUseCase.evaluateMatch(EvaluationCommand.builder()
                                    .expected(expectedOutput)
                                    .actual(maskedResponse)
                                    .method(evalMethod)
                                    .threshold(threshold)
                                    .build());
                        }

                        if (!matchResult.match() && command.getEvaluation().isEvaluateDetokenized()) {
                            matchResult = evaluationUseCase.evaluateMatch(EvaluationCommand.builder()
                                    .expected(expectedOutput)
                                    .actual(actualResponse)
                                    .method(evalMethod)
                                    .threshold(threshold)
                                    .build());
                        }

                        boolean match = matchResult.match();
                        double scoreValue = matchResult.score();

                        // 2. Record Score to Langfuse for visibility in experiments dashboard
                        evaluationPort.recordScore(RecordScoreCommand.builder()
                                .traceId(realTraceId)
                                .observationId(realSpanId)
                                .scoreName(command.getScoreName())
                                .value(scoreValue)
                                .comment(String.format("Mode: %s | Reason: %s", evalTypeStr, matchResult.reason()))
                                .build());

                        ExperimentResult.TestCaseResult result = ExperimentResult.TestCaseResult.builder()
                                .input(inputQuestion)
                                .expected(expectedOutput)
                                .actual(actualResponse)
                                .match(match)
                                .score(scoreValue)
                                .traceId(realTraceId)
                                .build();

                        // 3. Link back to dataset
                        evaluationPort.linkDatasetRunItem(command.getRunName(), itemId, realTraceId, realSpanId);

                        return result;
                    });

            if (detail.isMatch()) passedCount++;
            details.add(detail);
        }

        double accuracy = (double) passedCount / items.size();
        long executionTimeMs = System.currentTimeMillis() - startTime;
        log.info("[EXPERIMENT] Completed: {}/{} passed ({}%), took {}ms",
                passedCount, items.size(), accuracy * 100, executionTimeMs);

        // Advanced Metrics
        var confusionMatrix = evaluationUseCase.calculateConfusionMatrix(details);
        double f1Score = evaluationUseCase.calculateF1Score(confusionMatrix);
        Double averageReasonScore = evaluationUseCase.calculateAverageReasonScore(details);

        // Update Root Observation output with summary
        if (rootObs != null) {
            rootObs.highCardinalityKeyValue(ObservabilityConstants.TAG_OUTPUT, String.format("Completed: %d/%d passed (%.1f%%)\nF1: %.2f\nTime: %dms",
                    passedCount, items.size(), accuracy * 100, f1Score, executionTimeMs));
        }

        return ExperimentResult.builder()
                .runName(command.getRunName())
                .datasetName(command.getDatasetName())
                .total(items.size())
                .passed(passedCount)
                .failed(items.size() - passedCount)
                .accuracy(accuracy)
                .f1Score(f1Score)
                .confusionMatrix(confusionMatrix)
                .averageReasonScore(averageReasonScore)
                .executionTimeMs(executionTimeMs)
                .details(details)
                .build();
    }


    private String extractMediaToken(Map<String, Object> metadata) {
        return MediaTokenUtils.extractMediaToken(metadata, "attachment");
    }

    @SuppressWarnings("unchecked")
    private String extractField(Map<String, Object> item, String parentKey, String fieldName) {
        if (fieldName == null) return "";

        try {
            // 1. Try direct keys with various casings
            String[] candidates = {
                    fieldName,
                    CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, fieldName.replace(" ", "_")),
                    CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, fieldName),
                    "expectedOutput", "expected_output", "input", "output" // common fallbacks
            };

            for (String key : candidates) {
                Object val = item.get(key);
                if (val != null && !(val instanceof Map)) return val.toString();
            }

            // 2. Try inside metadata or nested parent
            for (String pk : new String[]{parentKey, "metadata", "input", "output"}) {
                Object parent = item.get(pk);
                if (parent instanceof Map) {
                    Map<String, Object> pMap = (Map<String, Object>) parent;
                    for (String key : candidates) {
                        Object val = pMap.get(key);
                        if (val != null) return val.toString();
                    }
                }
            }

            return "";
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public HumanEvaluationResult aggregateHumanScores(String runName, String humanScoreName, String llmScoreName) {
        long startTime = System.currentTimeMillis();
        log.info("[EXPERIMENT] Aggregating human scores for run: {}, human: {}, llm: {}",
                runName, humanScoreName, llmScoreName);

        // Langfuse API limit: 100 per page for scores (for POC simplicity, just fetch first 100)
        List<ScoreResult> scores = evaluationPort.fetchScores(1, 100);

        // Group by traceId
        Map<String, Double> humanMap = new HashMap<>();
        Map<String, Double> llmMap = new HashMap<>();

        for (ScoreResult score : scores) {
            if (humanScoreName.equalsIgnoreCase(score.name())) {
                humanMap.put(score.traceId(), score.value());
            } else if (llmScoreName.equalsIgnoreCase(score.name())) {
                llmMap.put(score.traceId(), score.value());
            }
        }

        int humanTotal = 0, humanPos = 0, humanNeg = 0;
        int llmTotal = 0, llmPos = 0, llmNeg = 0;
        int agreementCount = 0;
        Map<String, String> disagreements = new HashMap<>();

        for (String tid : humanMap.keySet()) {
            double hVal = humanMap.get(tid);
            humanTotal++;
            if (hVal > 0.5) humanPos++;
            else humanNeg++;

            if (llmMap.containsKey(tid)) {
                double lVal = llmMap.get(tid);
                llmTotal++;
                if (lVal > 0.5) llmPos++;
                else llmNeg++;

                boolean hBool = hVal > 0.5;
                boolean lBool = lVal > 0.5;

                if (hBool == lBool) {
                    agreementCount++;
                } else {
                    disagreements.put(tid, String.format("Human:%s, LLM:%s", hBool, lBool));
                }
            }
        }

        double agreementRate = humanTotal > 0 ? (double) agreementCount / humanTotal : 0.0;

        return HumanEvaluationResult.builder()
                .runName(runName)
                .scoreName(humanScoreName)
                .humanTotalCount(humanTotal)
                .humanPositiveCount(humanPos)
                .humanNegativeCount(humanNeg)
                .llmTotalCount(llmTotal)
                .llmPositiveCount(llmPos)
                .llmNegativeCount(llmNeg)
                .agreementRate(agreementRate)
                .disagreementCount(disagreements.size())
                .disagreements(disagreements)
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }
}
