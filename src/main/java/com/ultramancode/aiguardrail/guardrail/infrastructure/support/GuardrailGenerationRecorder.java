package com.ultramancode.aiguardrail.guardrail.infrastructure.support;

import com.ultramancode.aiguardrail.common.llm.application.port.out.LlmPort;
import com.ultramancode.aiguardrail.common.observability.GenerationRecordResult;
import com.ultramancode.aiguardrail.common.observability.TraceUtils;
import com.ultramancode.aiguardrail.common.observability.domain.GenerationAttachment;
import com.ultramancode.aiguardrail.common.observability.port.out.ObservabilityPort;
import com.ultramancode.aiguardrail.common.observability.support.GenerationRecordSupport;
import com.ultramancode.aiguardrail.common.util.TraceContentPolicy;
import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GuardrailGenerationRecorder {

    private final ObservabilityPort observabilityPort;
    private final LlmPort llmPort;

    @Value("${observability.upload-raw-attachment:false}")
    private boolean uploadRawAttachment;

    public GenerationRecordResult record(
            String traceId,
            String operationName,
            String vendor,
            String model,
            String input,
            String output,
            long startTime,
            boolean traceRawContent,
            PiiUseCase piiService,
            GenerationAttachment attachment
    ) {
        if (!TraceUtils.isValid(traceId)) {
            log.warn("[OBSERVABILITY] 생성 기록을 건너뜁니다. 유효하지 않은 traceId, operation={}", operationName);
            return GenerationRecordResult.empty();
        }

        try {
            String safeInput = TraceContentPolicy.resolve(input, traceRawContent, piiService::tokenize);
            String safeOutput = TraceContentPolicy.resolve(output, traceRawContent, piiService::tokenize);
            return GenerationRecordSupport.recordGenerationSafely(
                    observabilityPort,
                    llmPort,
                    traceId,
                    operationName,
                    vendor,
                    model,
                    safeInput,
                    safeOutput,
                    startTime,
                    attachment,
                    "[OBSERVABILITY]"
            );
        } catch (RuntimeException e) {
            log.warn("[OBSERVABILITY] Failed to prepare generation record. operation={}, cause={}",
                    operationName, e.getMessage(), e);
            return GenerationRecordResult.empty();
        }
    }

    public GenerationRecordResult recordFromCommand(
            String traceId,
            String operationName,
            PiiChatCommand command,
            String output,
            long startTime,
            boolean traceRawContent,
            PiiUseCase piiService,
            String extractedText
    ) {
        GenerationAttachment attachment = null;
        if (command.getFile() != null && !command.getFile().isEmpty()) {
            attachment = buildAttachment(command, extractedText);
        }

        return record(
                traceId,
                operationName,
                command.getVendor(),
                command.getModel(),
                command.getText(),
                output,
                startTime,
                traceRawContent,
                piiService,
                attachment
        );
    }

    private GenerationAttachment buildAttachment(PiiChatCommand command, String extractedText) {
        if (!uploadRawAttachment) {
            return null;
        }

        return new GenerationAttachment(
                command.getFile().getOriginalFilename(),
                command.getFile().getContentType(),
                command.getFile().getBytes(),
                extractedText
        );
    }
}
