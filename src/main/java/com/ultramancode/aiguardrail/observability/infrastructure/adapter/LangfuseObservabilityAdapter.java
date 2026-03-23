package com.ultramancode.aiguardrail.observability.infrastructure.adapter;

import com.ultramancode.aiguardrail.common.integration.langfuse.client.LangfuseScoreClient;
import com.ultramancode.aiguardrail.common.observability.GenerationRecordResult;
import com.ultramancode.aiguardrail.common.observability.TraceUtils;
import com.ultramancode.aiguardrail.common.observability.command.RecordGenerationCommand;
import com.ultramancode.aiguardrail.common.observability.command.RecordScoreCommand;
import com.ultramancode.aiguardrail.common.observability.port.out.ObservabilityPort;
import com.ultramancode.aiguardrail.observability.infrastructure.adapter.component.GenerationRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Langfuse 관측성 어댑터 (Thin Facade)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseObservabilityAdapter implements ObservabilityPort {

    private final LangfuseScoreClient scoreClient;
    private final GenerationRecorder generationRecorder;

    @Override
    public GenerationRecordResult recordGeneration(RecordGenerationCommand command) {
        if (command == null) {
            log.warn("[OBSERVABILITY] Skip generation recording: command is null");
            return GenerationRecordResult.empty();
        }

        if (!TraceUtils.isValid(command.getTraceId())) {
            log.warn(
                    "[OBSERVABILITY] Skip generation recording due to invalid traceId. operation={}",
                    command.getName()
            );
            return GenerationRecordResult.empty();
        }
        return generationRecorder.record(command);
    }

    @Override
    public void recordScore(RecordScoreCommand command) {
        if (command == null) {
            log.warn("[OBSERVABILITY] Skip score recording: command is null");
            return;
        }

        if (!TraceUtils.isValid(command.traceId())) {
            log.warn(
                    "[OBSERVABILITY] Skip score recording due to invalid traceId. scoreName={}, value={}",
                    command.scoreName(),
                    command.value()
            );
            return;
        }
        scoreClient.recordScore(command);
    }

    @Override
    public void traceInput(String input) {
        TraceUtils.tagSpanInput(input);
    }

    @Override
    public void traceOutput(String output) {
        TraceUtils.tagSpanOutput(output);
    }


}
