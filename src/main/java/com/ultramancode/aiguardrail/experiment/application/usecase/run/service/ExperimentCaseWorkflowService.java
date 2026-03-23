package com.ultramancode.aiguardrail.experiment.application.usecase.run.service;

import com.ultramancode.aiguardrail.common.observability.LangfuseConstants;
import com.ultramancode.aiguardrail.common.observability.TraceIdResolver;
import com.ultramancode.aiguardrail.common.observability.TraceUtils;
import com.ultramancode.aiguardrail.common.observability.domain.ObservationType;
import com.ultramancode.aiguardrail.common.util.ErrorMessageResolver;
import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentPiiPort;
import com.ultramancode.aiguardrail.experiment.application.result.CaseEvaluationOutcome;
import com.ultramancode.aiguardrail.experiment.application.result.ExperimentResult;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.assembler.ExperimentResultAssembler;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.support.ExperimentCaseExecutionSupport;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.support.ExperimentCaseInputSupport;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.support.ExperimentCaseObservationSupport;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentCaseWorkflowService {

    private static final String TAG_EXP_RUN_NAME = "experiment.run_name";
    private static final String TAG_DATASET_ITEM_ID = "dataset.item_id";

    private final ExperimentCaseInputSupport caseInputSupport;
    private final ExperimentCaseObservationSupport caseObservationSupport;
    private final ExperimentCaseExecutionSupport caseExecutionSupport;
    private final ExperimentResultAssembler resultAssembler;
    private final ExperimentCaseEvaluationService caseEvaluationService;
    private final ObservationRegistry observationRegistry;
    private final ExperimentPiiPort experimentPiiPort;

    public ExperimentResult.TestCaseResult processTestCase(
            RunExperimentCommand command,
            Map<String, Object> item,
            int itemIndex,
            String resolvedSystemPrompt
    ) {
        String itemId = caseInputSupport.resolveItemId(item, itemIndex);
        String inputQuestion = "";
        String expectedOutput = "";
        String expectedReason = "";

        try {
            ExperimentCaseInputSupport.ExperimentCaseInput testCaseInput =
                    caseInputSupport.buildCaseInput(command, itemId, item);
            inputQuestion = testCaseInput.inputQuestion();
            expectedOutput = testCaseInput.expectedOutput();
            expectedReason = testCaseInput.expectedReason();

            if (testCaseInput.validationError() != null && !testCaseInput.validationError().isBlank()) {
                log.warn("[EXPERIMENT] {} (itemId={})", testCaseInput.validationError(), itemId);
                return buildFailedResult(testCaseInput, testCaseInput.validationError());
            }

            String requiredFileError = caseInputSupport.validateRequiredFile(
                    command.getMode(),
                    command.getTarget(),
                    testCaseInput.file()
            );
            if (requiredFileError != null) {
                log.warn("[EXPERIMENT] {} (itemId={})", requiredFileError, itemId);
                return buildFailedResult(testCaseInput, requiredFileError);
            }

            return executeObservedCase(command, itemId, testCaseInput, resolvedSystemPrompt);
        } catch (IllegalArgumentException e) {
            return handleCaseFailure(
                    itemId,
                    inputQuestion,
                    expectedOutput,
                    expectedReason,
                    e,
                    "IllegalArgumentException",
                    "Test case validation failed",
                    "Validation error during test case processing.",
                    false
            );
        } catch (RuntimeException e) {
            if (e instanceof IllegalStateException) {
                return handleCaseFailure(
                        itemId,
                        inputQuestion,
                        expectedOutput,
                        expectedReason,
                        e,
                        "IllegalStateException",
                        "Test case failed",
                        "System error during test case processing.",
                        true
                );
            }

            return handleCaseFailure(
                    itemId,
                    inputQuestion,
                    expectedOutput,
                    expectedReason,
                    e,
                    "UnexpectedRuntimeException",
                    "Test case failed with unexpected runtime error",
                    "Unexpected runtime error during test case processing.",
                    true
            );
        } finally {
            try {
                experimentPiiPort.clearContext();
            } catch (RuntimeException e) {
                log.warn(
                        "[EXPERIMENT] Failed to clear PII context. cause={}",
                        ErrorMessageResolver.resolve(e, "RuntimeException"),
                        e
                );
            }
        }
    }

    private ExperimentResult.TestCaseResult handleCaseFailure(
            String itemId,
            String inputQuestion,
            String expectedOutput,
            String expectedReason,
            RuntimeException exception,
            String errorType,
            String logMessagePrefix,
            String failureMessagePrefix,
            boolean logWithStackTrace
    ) {
        String resolvedErrorMessage = ErrorMessageResolver.resolve(exception, errorType);
        if (logWithStackTrace) {
            log.error(
                    "[EXPERIMENT] {} (itemId={}): {}",
                    logMessagePrefix,
                    itemId,
                    resolvedErrorMessage,
                    exception
            );
        } else {
            log.warn("[EXPERIMENT] {} (itemId={}): {}", logMessagePrefix, itemId, resolvedErrorMessage);
        }
        return buildFailedResult(
                inputQuestion,
                expectedOutput,
                expectedReason,
                buildCaseFailureMessage(
                        failureMessagePrefix,
                        itemId,
                        resolvedErrorMessage
                )
        );
    }

    private ExperimentResult.TestCaseResult buildFailedResult(
            ExperimentCaseInputSupport.ExperimentCaseInput testCaseInput,
            String errorMessage
    ) {
        return buildFailedResult(
                testCaseInput.inputQuestion(),
                testCaseInput.expectedOutput(),
                testCaseInput.expectedReason(),
                errorMessage
        );
    }

    private ExperimentResult.TestCaseResult buildFailedResult(
            String inputQuestion,
            String expectedOutput,
            String expectedReason,
            String errorMessage
    ) {
        return resultAssembler.buildFailedResult(
                inputQuestion,
                expectedOutput,
                expectedReason,
                errorMessage
        );
    }

    private String buildCaseFailureMessage(String prefix, String itemId, String cause) {
        return prefix + " itemId=" + itemId + ", cause=" + cause;
    }

    private ExperimentResult.TestCaseResult executeObservedCase(
            RunExperimentCommand command,
            String itemId,
            ExperimentCaseInputSupport.ExperimentCaseInput testCaseInput,
            String resolvedSystemPrompt
    ) {
        return Observation.createNotStarted("experiment-case-workflow", observationRegistry)
                .lowCardinalityKeyValue(LangfuseConstants.TAG_OBSERVATION_TYPE, ObservationType.CHAIN.getValue())
                .highCardinalityKeyValue(TAG_EXP_RUN_NAME, command.getRunName())
                .highCardinalityKeyValue(TAG_DATASET_ITEM_ID, itemId)
                .observe(() -> executeCaseAndBuildResult(command, itemId, testCaseInput, resolvedSystemPrompt));
    }

    private ExperimentResult.TestCaseResult executeCaseAndBuildResult(
            RunExperimentCommand command,
            String itemId,
            ExperimentCaseInputSupport.ExperimentCaseInput testCaseInput,
            String resolvedSystemPrompt
    ) {
        String traceId = TraceIdResolver.fromSpanContext(Span.current().getSpanContext());

        ExperimentCaseExecutionSupport.CaseExecutionOutput executionOutput = caseExecutionSupport.executeCaseByMode(
                command,
                testCaseInput.inputQuestion(),
                testCaseInput.file(),
                traceId,
                resolvedSystemPrompt
        );
        String observationId = executionOutput.observationId();
        if (observationId != null && observationId.isBlank()) {
            observationId = null;
        }

        caseObservationSupport.tagCurrentCaseObservation(
                testCaseInput.metadata(),
                testCaseInput.inputQuestion(),
                executionOutput.actualResponse()
        );

        CaseEvaluationOutcome evaluationOutcome = caseEvaluationService.performEvaluation(
                command,
                testCaseInput.expectedOutput(),
                executionOutput.actualResponse(),
                executionOutput.maskedResponse(),
                executionOutput.maskedFallbackUsed()
        );
        Double reasonScore = caseEvaluationService.evaluateReasonScore(
                command,
                itemId,
                testCaseInput.expectedReason(),
                executionOutput.actualResponse()
        );
        String recordingErrorMessage = null;
        if (!TraceUtils.isValid(traceId)) {
            recordingErrorMessage = "Invalid traceId from current span. Skipped Langfuse score/link recording.";
            log.warn(
                    "[EXPERIMENT] {} runName={}, itemId={}",
                    recordingErrorMessage,
                    command.getRunName(),
                    itemId
            );
        } else {
            try {
                caseEvaluationService.recordResults(
                        command,
                        itemId,
                        traceId,
                        observationId,
                        evaluationOutcome.matchResult().score(),
                        evaluationOutcome.matchResult().reason(),
                        evaluationOutcome.comparedValueLabel()
                );
            } catch (RuntimeException e) {
                recordingErrorMessage = ErrorMessageResolver.resolve(e, "RuntimeException");
                log.warn(
                        "[EXPERIMENT] Score/link recording failed but case result is preserved. runName={}, itemId={}, cause={}",
                        command.getRunName(),
                        itemId,
                        recordingErrorMessage,
                        e
                );
            }
        }

        return resultAssembler.buildSucceededResult(
                testCaseInput.inputQuestion(),
                testCaseInput.expectedOutput(),
                testCaseInput.expectedReason(),
                executionOutput.actualResponse(),
                evaluationOutcome,
                reasonScore,
                traceId,
                observationId,
                recordingErrorMessage
        );
    }
}
