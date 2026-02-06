package com.ultramancode.aiguardrail.observability.infrastructure.adapter;

import com.ultramancode.aiguardrail.multimodal.domain.GenerationAttachment;
import com.ultramancode.aiguardrail.common.observability.UsageResult;
import com.ultramancode.aiguardrail.observability.application.port.out.ObservabilityPort;
import com.ultramancode.aiguardrail.observability.infrastructure.client.LangfuseIngestionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.DefaultUsage;
import com.ultramancode.aiguardrail.observability.infrastructure.utils.TraceUtils;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Component;

import com.ultramancode.aiguardrail.guardrail.application.port.out.GuardrailObservabilityPort;
import com.ultramancode.aiguardrail.common.observability.RecordScoreCommand;
import com.ultramancode.aiguardrail.guardrail.application.domain.FetchedPrompt;

import java.util.Optional;

import com.langfuse.client.LangfuseClient;
import org.springframework.beans.factory.annotation.Value;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
class LangfuseObservabilityAdapter implements ObservabilityPort, GuardrailObservabilityPort {

    private final LangfuseIngestionClient ingestionClient;
    private final LangfuseClient langfuseClient;

    @Value("${langfuse.prompt-label:production}")
    private String promptLabel;

    @Override
    public void recordGeneration(String traceId, String name, String modelName,
                                 String input, String output,
                                 GenerationAttachment attachment,
                                 UsageResult usage,
                                 long startTime, long endTime) {

        Usage springUsage = null;
        if (usage != null) {
            springUsage = new DefaultUsage((int) usage.promptTokens(), (int) usage.completionTokens());
        }

        if (attachment != null) {
            if (attachment.contentType() != null && attachment.contentType().startsWith("image/")) {
                // Vision Logic
                ingestionClient.recordVisionGenerationWithMedia(
                        traceId, modelName,
                        attachment.contentBytes(), attachment.contentType(),
                        input, output,
                        startTime, endTime, springUsage
                );
            } else {
                // PDF/File Logic
                String extractedText = attachment.extractedText() != null ? attachment.extractedText() : "";

                ingestionClient.recordGenerationWithAttachment(
                        traceId, name, modelName,
                        attachment.fileName(), attachment.contentBytes(),
                        input, extractedText, output,
                        startTime, endTime
                );
            }
        }
    }

    @Override
    public void traceInput(String input) {
        TraceUtils.tagSpanInput(input);
    }

    @Override
    public void traceOutput(String output) {
        TraceUtils.tagSpanOutput(output);
    }

    @Override
    public boolean isValidTraceId(String traceId) {
        return TraceUtils.isValid(traceId);
    }

    @Override
    public Optional<FetchedPrompt> fetchPrompt(String promptName) {
        try {
            // SDK 0.1.2: prompts().get(name) defaults to production
            var prompt = langfuseClient.prompts().get(promptName);

            if (prompt.isText() && prompt.getText().isPresent()) {
                var textPrompt = prompt.getText().get();
                log.info("[PROMPT] Successfully fetched prompt '{}' from Langfuse (v{})", promptName, textPrompt.getVersion());
                return Optional.of(new FetchedPrompt(
                        textPrompt.getPrompt().stripIndent(),
                        promptName,
                        textPrompt.getVersion()
                ));
            }
            return Optional.empty();

        } catch (Exception e) {
            log.warn("[PROMPT] Failed to fetch prompt '{}' from Langfuse: {}", promptName, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void recordScore(RecordScoreCommand command) {
        ingestionClient.recordScore(
                command.traceId(),
                command.scoreName(),
                command.value(),
                command.comment()
        );
    }


}
