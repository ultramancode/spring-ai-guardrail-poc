package com.ultramancode.aiguardrail.common.integration.langfuse.client;

import com.langfuse.client.LangfuseClient;
import com.langfuse.client.resources.commons.types.CreateScoreValue;
import com.langfuse.client.resources.score.types.CreateScoreRequest;
import com.ultramancode.aiguardrail.common.observability.command.RecordScoreCommand;
import com.ultramancode.aiguardrail.common.observability.TraceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Langfuse SDK 기반 점수 기록 클라이언트입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseScoreClient {

    private final LangfuseClient langfuseClient;

    @Value("${langfuse.score.fail-open:false}")
    private boolean scoreFailOpen;

    public void recordScore(RecordScoreCommand command) {
        if (command == null) {
            log.warn("[LANGFUSE-SCORE] Skip score recording: command is null");
            return;
        }
        if (!TraceUtils.isValid(command.traceId())) {
            log.warn(
                    "[LANGFUSE-SCORE] Skip score recording due to invalid traceId. scoreName={}, value={}",
                    command.scoreName(),
                    command.value()
            );
            return;
        }

        try {
            langfuseClient.score().create(CreateScoreRequest.builder()
                    .name(command.scoreName())
                    .value(CreateScoreValue.of(command.value()))
                    .traceId(command.traceId())
                    .comment(command.comment())
                    .observationId(command.observationId())
                    .build());
            log.debug("[LANGFUSE-SCORE] Recorded score: {} = {}", command.scoreName(), command.value());
        } catch (RuntimeException e) {
            if (scoreFailOpen) {
                log.warn("[LANGFUSE-SCORE] Failed to record score: {}", e.getMessage(), e);
                return;
            }
            throw new IllegalStateException("Failed to record score to Langfuse", e);
        }
    }
}
