package com.ultramancode.aiguardrail.guardrail.infrastructure.adapter.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultramancode.aiguardrail.common.observability.LangfuseConstants;
import com.ultramancode.aiguardrail.common.observability.domain.ObservationType;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Objects;

/**
 * ToolCallback을 감싸서 도구 실행 전 인자를 디토큰화하고,
 * 도구 실행 후 결과를 다시 토큰화하는 래퍼입니다.
 */
@Slf4j
public class PiiToolCallbackWrapper implements ToolCallback {

    private static final String TAG_PII_DETOKENIZED = "pii.detokenized";
    private static final String TAG_TOOL_RESULT_MASKED = "tool.result.masked";
    private static final String TAG_AUDIT_DECRYPTED_ARGS = "audit.decrypted.args";
    private static final String TAG_AUDIT_DECRYPTED_RESULT = "audit.decrypted.result";

    private final ToolCallback delegate;
    private final PiiUseCase piiService;
    private final ObjectMapper objectMapper;
    private final ObservationRegistry observationRegistry;
    private final boolean auditMode;
    private final boolean traceRawContent;

    public PiiToolCallbackWrapper(
            ToolCallback delegate,
            PiiUseCase piiService,
            ObjectMapper objectMapper,
            ObservationRegistry observationRegistry,
            boolean auditMode,
            boolean traceRawContent
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.piiService = Objects.requireNonNull(piiService, "piiService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry must not be null");
        this.auditMode = auditMode;
        this.traceRawContent = traceRawContent;
    }

    public PiiToolCallbackWrapper(
            ToolCallback delegate,
            PiiUseCase piiService,
            ObjectMapper objectMapper,
            ObservationRegistry observationRegistry,
            boolean auditMode
    ) {
        this(delegate, piiService, objectMapper, observationRegistry, auditMode, false);
    }

    public PiiToolCallbackWrapper(
            ToolCallback delegate,
            PiiUseCase piiService,
            ObjectMapper objectMapper,
            ObservationRegistry observationRegistry
    ) {
        this(delegate, piiService, objectMapper, observationRegistry, false, false);
    }

    @NotNull
    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @NotNull
    @Override
    public String call(@NotNull String toolInput) {
        Observation obs = Observation.createNotStarted(getToolDefinition().name(), observationRegistry)
                .lowCardinalityKeyValue(
                        LangfuseConstants.TAG_OBSERVATION_TYPE,
                        ObservationType.TOOL.getValue()
                );

        String observedResult = obs.observe(() -> {
            try {
                // 1. JSON 입력을 파싱합니다.
                Object parsed = objectMapper.readValue(toolInput, Object.class);
                logToolInput(getToolDefinition().name(), parsed, toolInput);

                // 2. 도구 실행 전 토큰을 원문으로 복원합니다.
                Object cleanArgs = piiService.detokenizeRecursive(parsed);
                recordInputAudit(parsed, cleanArgs);

                // 3. 복원된 인자로 도구를 실행합니다.
                String cleanInput = objectMapper.writeValueAsString(cleanArgs);
                String result = delegate.call(cleanInput);
                if (result == null) {
                    log.warn("[FILTER] Tool '{}' returned null. Using empty string.", getToolDefinition().name());
                    return "";
                }

                // 4. 도구 출력은 다시 토큰화해서 반환합니다.
                String maskedResult = piiService.tokenizeWithoutObservation(result);
                recordOutputAudit(result, maskedResult);
                if (maskedResult == null) {
                    log.warn("[FILTER] Masked result is null for tool '{}'. Using empty string.",
                            getToolDefinition().name());
                    return "";
                }
                return maskedResult;
            } catch (JsonProcessingException e) {
                // JSON 파싱 실패 시 문자열 입력으로 간주해 동일 절차를 수행합니다.
                obs.error(e);
                logToolInput(getToolDefinition().name(), toolInput, toolInput);
                log.warn("[FILTER] Input is not valid JSON, treating as plain string.");
                String cleanInput = piiService.detokenizeWithoutObservation(toolInput);
                String result = delegate.call(cleanInput);
                if (result == null) {
                    log.warn("[FILTER] Tool '{}' returned null. Using empty string.", getToolDefinition().name());
                    return "";
                }

                String maskedResult = piiService.tokenizeWithoutObservation(result);
                if (maskedResult == null) {
                    log.warn("[FILTER] Masked result is null for tool '{}'. Using empty string.",
                            getToolDefinition().name());
                    return "";
                }
                return maskedResult;
            }
        });

        if (observedResult == null) {
            log.warn("[FILTER] Observation result is null for tool '{}'. Using empty string.",
                    getToolDefinition().name());
            return "";
        }
        return observedResult;
    }

    private void logToolInput(String toolName, Object parsedInput, String rawFallbackInput) {
        if (traceRawContent) {
            log.info("[FILTER] Intercepted Tool Call: '{}'. Input: {}", toolName, parsedInput);
            return;
        }

        String rawText = safeSerializeInput(parsedInput, rawFallbackInput);
        String maskedText = "[masked] length=" + rawText.length();

        log.info(
                "[FILTER] Intercepted Tool Call: '{}'. Input(masked): {}, rawLength={}, maskedLength={}",
                toolName,
                maskedText,
                rawText.length(),
                maskedText.length()
        );
    }

    private String safeSerializeInput(Object parsedInput, String rawFallbackInput) {
        try {
            if (parsedInput == null) {
                return "";
            }
            if (parsedInput instanceof String textValue) {
                return textValue;
            }
            return objectMapper.writeValueAsString(parsedInput);
        } catch (JsonProcessingException e) {
            if (rawFallbackInput == null) {
                return "";
            }
            return rawFallbackInput;
        }
    }

    private void recordInputAudit(Object original, Object cleaned) {
        if (!auditMode) {
            return;
        }
        Span span = Span.current();
        span.setAttribute(TAG_AUDIT_DECRYPTED_ARGS, String.valueOf(cleaned));
        log.warn("[AUDIT] Decrypted arguments recorded in trace: {}", cleaned);

        if (!Objects.equals(original, cleaned)) {
            log.info("[FILTER] Detokenized Arguments: {} -> {}", original, cleaned);
            span.setAttribute(TAG_PII_DETOKENIZED, true);
        }
    }

    private void recordOutputAudit(String original, String masked) {
        if (!auditMode) {
            return;
        }
        Span span = Span.current();
        span.setAttribute(TAG_AUDIT_DECRYPTED_RESULT, original);

        if (!Objects.equals(original, masked)) {
            log.info("[FILTER] Masked Tool Output: {} -> {}", original, masked);
            span.setAttribute(TAG_TOOL_RESULT_MASKED, true);
        }
    }
}
