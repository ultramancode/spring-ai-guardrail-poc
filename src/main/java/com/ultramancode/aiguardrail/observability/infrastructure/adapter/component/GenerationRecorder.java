package com.ultramancode.aiguardrail.observability.infrastructure.adapter.component;

import com.ultramancode.aiguardrail.common.integration.langfuse.client.LangfuseIngestionClient;
import com.ultramancode.aiguardrail.common.llm.LlmConstants;
import com.ultramancode.aiguardrail.common.observability.GenerationRecordResult;
import com.ultramancode.aiguardrail.common.observability.UsageResult;
import com.ultramancode.aiguardrail.common.observability.command.RecordGenerationCommand;
import com.ultramancode.aiguardrail.common.observability.domain.GenerationAttachment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationRecorder {

    private final LangfuseIngestionClient ingestionClient;

    @Value("${observability.upload-raw-attachment:false}")
    private boolean uploadRawAttachment;

    public GenerationRecordResult record(RecordGenerationCommand command) {
        GenerationAttachment attachment = command.getAttachment();
        if (attachment != null && !uploadRawAttachment) {
            attachment = null;
        }
        String observationId;

        if (attachment == null) {
            observationId = ingestionClient.recordGeneration(
                    command.getTraceId(),
                    command.getName(),
                    command.getModelName(),
                    command.getInput(),
                    command.getOutput(),
                    command.getPromptName(),
                    command.getStartTime(),
                    command.getEndTime()
            );
        } else if (isImageAttachment(attachment)) {
            observationId = ingestionClient.recordVisionGenerationWithMedia(
                    command.getTraceId(),
                    command.getModelName(),
                    attachment.contentBytes(),
                    attachment.contentType(),
                    command.getInput(),
                    command.getOutput(),
                    command.getStartTime(),
                    command.getEndTime(),
                    toSpringUsage(command.getUsage())
            );
        } else {
            observationId = ingestionClient.recordGenerationWithAttachment(
                    command.getTraceId(),
                    command.getName(),
                    command.getModelName(),
                    attachment.fileName(),
                    attachment.contentBytes(),
                    attachment.contentType(),
                    command.getInput(),
                    attachment.extractedText(),
                    command.getOutput(),
                    command.getStartTime(),
                    command.getEndTime()
            );
        }

        return new GenerationRecordResult(command.getTraceId(), observationId);
    }

    private boolean isImageAttachment(GenerationAttachment attachment) {
        if (attachment.contentType() == null) {
            return false;
        }
        return attachment.contentType().startsWith(LlmConstants.MEDIA_TYPE_IMAGE_PREFIX);
    }

    private @Nullable Usage toSpringUsage(@Nullable UsageResult usage) {
        if (usage == null) {
            return null;
        }

        int promptTokens = toIntTokenCount(usage.promptTokens(), "promptTokens");
        int completionTokens = toIntTokenCount(usage.completionTokens(), "completionTokens");
        return new DefaultUsage(promptTokens, completionTokens);
    }

    private int toIntTokenCount(long tokenCount, String fieldName) {
        if (tokenCount < 0) {
            log.warn("[OBSERVABILITY] {} was negative. Using 0 instead.", fieldName);
            return 0;
        }
        if (tokenCount > Integer.MAX_VALUE) {
            log.warn("[OBSERVABILITY] {} exceeded Integer.MAX_VALUE. Clamping value.", fieldName);
            return Integer.MAX_VALUE;
        }
        return (int) tokenCount;
    }
}
