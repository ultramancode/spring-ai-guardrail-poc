package com.ultramancode.aiguardrail.experiment.application.usecase.run.support;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import com.ultramancode.aiguardrail.common.observability.ObservabilityTags;
import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.support.dataset.DatasetItemIdResolver;
import com.ultramancode.aiguardrail.experiment.application.usecase.run.support.dataset.ExperimentFieldMapper;
import com.ultramancode.aiguardrail.experiment.domain.model.ExperimentMode;
import com.ultramancode.aiguardrail.experiment.domain.model.ExperimentTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExperimentCaseInputSupport {

    private static final String MISSING_FIELD_POLICY_FAIL = "fail";
    private static final String MISSING_FIELD_POLICY_WARN = "warn";

    private final ExperimentFieldMapper fieldMapper;
    private final ExperimentFileSupport fileSupport;
    private final DatasetItemIdResolver datasetItemIdResolver;

    @Value("${experiment.validation.strict-field-mapping:false}")
    private boolean strictFieldMapping;

    @Value("${experiment.validation.on-missing-field:warn}")
    private String missingFieldPolicy;

    public String resolveItemId(Map<String, Object> item, int itemIndex) {
        return datasetItemIdResolver.resolveOrDefault(item, datasetItemIdResolver.defaultItemId(itemIndex));
    }

    public ExperimentCaseInput buildCaseInput(
            RunExperimentCommand command,
            String itemId,
            Map<String, Object> item
    ) {
        String inputFieldPath = command.getFieldMapping().getInput();
        String expectedFieldPath = command.getFieldMapping().getExpected();
        String expectedReasonFieldPath = command.getFieldMapping().getExpectedReason();

        FieldValueResolution inputResolution = resolveRequiredFieldValue(
                itemId,
                item,
                "input",
                inputFieldPath
        );
        FieldValueResolution expectedResolution = resolveRequiredFieldValue(
                itemId,
                item,
                "expected",
                expectedFieldPath
        );
        if (command.getEvaluation().isEvaluateReason()
                && expectedReasonFieldPath != null
                && !expectedReasonFieldPath.isBlank()) {
            applyMissingFieldPolicy(itemId, item, expectedReasonFieldPath, "expectedReason");
        }

        String inputQuestion = inputResolution.value();
        String expectedOutput = expectedResolution.value();
        String expectedReason = fieldMapper.extractField(item, expectedReasonFieldPath);
        String validationError = mergeValidationError(
                inputResolution.validationError(),
                expectedResolution.validationError()
        );

        if (validationError != null && !validationError.isBlank()) {
            return new ExperimentCaseInput(
                    inputQuestion,
                    expectedOutput,
                    expectedReason,
                    null,
                    null,
                    validationError
            );
        }

        Map<String, Object> metadata = extractMetadata(item, itemId);
        AttachmentFile file = null;
        if (metadata != null) {
            file = fileSupport.loadFile(metadata);
        }

        return new ExperimentCaseInput(
                inputQuestion,
                expectedOutput,
                expectedReason,
                metadata,
                file,
                validationError
        );
    }

    public String validateRequiredFile(ExperimentMode mode, ExperimentTarget target, AttachmentFile file) {
        if (mode == ExperimentMode.FULL_WORKFLOW) {
            return null;
        }

        if (requiresFile(target) && file == null) {
            return "Missing media file for target: " + target;
        }

        return null;
    }

    private void applyMissingFieldPolicy(
            String itemId,
            Map<String, Object> item,
            String fieldPath,
            String fieldLabel
    ) {
        if (!fieldMapper.hasField(item, fieldPath)) {
            String message = "Mapped field not found in dataset item: " + fieldPath;
            if (isMissingFieldStrict()) {
                throw new IllegalArgumentException(message);
            }
            log.warn("[EXPERIMENT] {} (itemId={}, fieldLabel={})", message, itemId, fieldLabel);
        }
    }

    private boolean isMissingFieldStrict() {
        if (strictFieldMapping) {
            return true;
        }

        if (missingFieldPolicy == null || missingFieldPolicy.isBlank()) {
            return false;
        }

        if (MISSING_FIELD_POLICY_FAIL.equalsIgnoreCase(missingFieldPolicy)) {
            return true;
        }

        if (MISSING_FIELD_POLICY_WARN.equalsIgnoreCase(missingFieldPolicy)) {
            return false;
        }

        log.warn(
                "[EXPERIMENT] Invalid experiment.validation.on-missing-field policy: {}. Fallback to warn.",
                missingFieldPolicy
        );
        return false;
    }

    private FieldValueResolution resolveRequiredFieldValue(
            String itemId,
            Map<String, Object> item,
            String fieldLabel,
            String fieldPath
    ) {
        String value = fieldMapper.extractField(item, fieldPath);
        String validationError = null;

        if (value == null || value.isBlank()) {
            validationError = "Resolved value is missing. itemId=" + itemId
                    + ", fieldLabel=" + fieldLabel
                    + ", fieldPath=" + fieldPath;
        }

        if (validationError == null) {
            return FieldValueResolution.valid(value);
        }

        if (isMissingFieldStrict()) {
            throw new IllegalArgumentException(validationError);
        }

        log.warn("[EXPERIMENT] {} (itemId={})", validationError, itemId);
        return FieldValueResolution.warn(validationError);
    }

    private String mergeValidationError(String firstError, String secondError) {
        if (firstError == null || firstError.isBlank()) {
            return secondError;
        }
        if (secondError == null || secondError.isBlank()) {
            return firstError;
        }
        return firstError + " | " + secondError;
    }

    private Map<String, Object> extractMetadata(Map<String, Object> item, String itemId) {
        Object rawMetadata = item.get(ObservabilityTags.KEY_METADATA);
        if (rawMetadata == null) {
            return null;
        }

        if (!(rawMetadata instanceof Map<?, ?> metadataMap)) {
            throw new IllegalArgumentException(
                    "Dataset metadata must be an object. itemId=" + itemId
            );
        }

        Map<String, Object> normalizedMetadata = new LinkedHashMap<>();
        metadataMap.forEach((key, value) -> {
            if (key != null) {
                normalizedMetadata.put(String.valueOf(key), value);
            }
        });
        return normalizedMetadata;
    }

    private boolean requiresFile(ExperimentTarget target) {
        if (target == ExperimentTarget.ANALYZE_PDF) {
            return true;
        }
        if (target == ExperimentTarget.ANALYZE_IMAGE) {
            return true;
        }
        return false;
    }

    private record FieldValueResolution(String value, String validationError) {
        private static FieldValueResolution valid(String value) {
            return new FieldValueResolution(value, null);
        }

        private static FieldValueResolution warn(String validationError) {
            return new FieldValueResolution("", validationError);
        }
    }

    public record ExperimentCaseInput(
            String inputQuestion,
            String expectedOutput,
            String expectedReason,
            Map<String, Object> metadata,
            AttachmentFile file,
            String validationError
    ) {
    }
}
