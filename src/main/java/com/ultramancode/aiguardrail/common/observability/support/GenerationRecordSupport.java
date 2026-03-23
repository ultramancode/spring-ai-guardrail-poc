package com.ultramancode.aiguardrail.common.observability.support;

import com.ultramancode.aiguardrail.common.llm.ModelNameResolver;
import com.ultramancode.aiguardrail.common.llm.application.port.out.LlmPort;
import com.ultramancode.aiguardrail.common.observability.GenerationRecordResult;
import com.ultramancode.aiguardrail.common.observability.command.RecordGenerationCommand;
import com.ultramancode.aiguardrail.common.observability.domain.GenerationAttachment;
import com.ultramancode.aiguardrail.common.observability.port.out.ObservabilityPort;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GenerationRecordSupport {

    public static String resolveModelNameForRecording(LlmPort llmPort, String vendor, String model) {
        if (model != null && !model.isBlank()) {
            return ModelNameResolver.resolve(vendor, model);
        }

        String resolvedVendor = vendor;
        if (resolvedVendor == null || resolvedVendor.isBlank()) {
            resolvedVendor = null;
        }

        String defaultModelName = llmPort.getModelName(resolvedVendor);
        if (defaultModelName == null || defaultModelName.isBlank() || "unknown".equalsIgnoreCase(defaultModelName)) {
            return ModelNameResolver.resolve(vendor, model);
        }

        String vendorName = resolvedVendor;
        if (vendorName == null || vendorName.isBlank()) {
            vendorName = "default";
        }
        return vendorName + "/" + defaultModelName;
    }

    public static GenerationRecordResult recordSafely(
            ObservabilityPort observabilityPort,
            RecordGenerationCommand command,
            String logPrefix
    ) {
        try {
            return observabilityPort.recordGeneration(command);
        } catch (RuntimeException e) {
            log.warn("{} Failed to record generation. cause={}", logPrefix, e.getMessage(), e);
            return GenerationRecordResult.empty();
        }
    }

    public static GenerationRecordResult recordGenerationSafely(
            ObservabilityPort observabilityPort,
            LlmPort llmPort,
            String traceId,
            String operationName,
            String vendor,
            String model,
            String input,
            String output,
            long startTime,
            GenerationAttachment attachment,
        String logPrefix
    ) {
        try {
            String modelName = resolveModelNameForRecording(llmPort, vendor, model);
            RecordGenerationCommand command = RecordGenerationCommand.builder()
                    .traceId(traceId)
                    .name(operationName)
                    .modelName(modelName)
                    .input(input)
                    .output(output)
                    .attachment(attachment)
                    .startTime(startTime)
                    .endTime(System.currentTimeMillis())
                    .build();
            return recordSafely(observabilityPort, command, logPrefix);
        } catch (RuntimeException e) {
            log.warn(
                    "{} Failed to prepare generation record. operation={}, cause={}",
                    logPrefix,
                    operationName,
                    e.getMessage(),
                    e
            );
            return GenerationRecordResult.empty();
        }
    }
}
