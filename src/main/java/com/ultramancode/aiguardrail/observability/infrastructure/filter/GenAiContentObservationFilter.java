package com.ultramancode.aiguardrail.observability.infrastructure.filter;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Content;
import org.springframework.util.CollectionUtils;

import java.util.stream.Collectors;

import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.trace.Span;

/**
 * Custom ObservationFilter to include full prompt and completion content in tracing spans.
 * This is required for Spring AI 1.1.0+ as it no longer includes content in high-cardinality keys by default.
 */
public class GenAiContentObservationFilter implements ObservationFilter {

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ChatModelObservationContext chatContext)) {
            return context;
        }

        // Add Prompt Content
        if (!CollectionUtils.isEmpty(chatContext.getRequest().getInstructions())) {
            String prompt = chatContext.getRequest().getInstructions().stream()
                    .map(Content::getText)
                    .collect(Collectors.joining("\n"));
            chatContext.addHighCardinalityKeyValue(KeyValue.of("gen_ai.prompt", prompt));
        }

        // Add Completion Content
        ChatResponse response = chatContext.getResponse();
        if (response != null && !CollectionUtils.isEmpty(response.getResults())) {
            String completion = response.getResults().stream()
                    .map(Generation::getOutput)
                    .map(Content::getText)
                    .collect(Collectors.joining("\n"));
            chatContext.addHighCardinalityKeyValue(KeyValue.of("gen_ai.completion", completion));
        }

        return chatContext;
    }
}
