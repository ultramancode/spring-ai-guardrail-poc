package com.ultramancode.aiguardrail.experiment.application.usecase.run.support;

import com.ultramancode.aiguardrail.common.observability.ObservabilityTags;
import com.ultramancode.aiguardrail.common.util.TraceContentPolicy;
import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentPiiPort;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 실험 케이스 관측 태깅을 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperimentCaseObservationSupport {

    private final ObservationRegistry observationRegistry;
    private final ExperimentMediaMetadataSupport mediaMetadataSupport;
    private final ExperimentPiiPort experimentPiiPort;

    @Value("${guardrail.pii.trace-raw-content:false}")
    private boolean traceRawContent;

    public void tagCurrentCaseObservation(
            Map<String, Object> metadata,
            String inputQuestion,
            String actualResponse
    ) {
        Observation currentObs = observationRegistry.getCurrentObservation();
        if (currentObs == null) {
            return;
        }

        try {
            // 관측 태깅 실패가 실험 케이스 실패로 전파되지 않도록 격리한다.
            String traceInput = resolveTraceContent(inputQuestion);
            String mediaId = mediaMetadataSupport.extractMediaId(metadata);
            if (mediaId != null) {
                currentObs.highCardinalityKeyValue(
                        ObservabilityTags.KEY_INPUT,
                        mediaId + "\n" + traceInput
                );
            } else {
                currentObs.highCardinalityKeyValue(ObservabilityTags.KEY_INPUT, traceInput);
            }
            currentObs.highCardinalityKeyValue(ObservabilityTags.KEY_OUTPUT, resolveTraceContent(actualResponse));
        } catch (RuntimeException e) {
            log.warn(
                    "[EXPERIMENT] Failed to tag case observation. Skipping observation tags. cause={}",
                    e.getMessage(),
                    e
            );
        }
    }

    private String resolveTraceContent(String rawContent) {
        return TraceContentPolicy.resolve(rawContent, traceRawContent, experimentPiiPort::tokenize);
    }
}
