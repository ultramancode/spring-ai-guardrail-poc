package com.ultramancode.aiguardrail.observability.infrastructure.filter;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.content.Content;
import org.springframework.util.CollectionUtils;

import java.util.stream.Collectors;

/**
 * 프롬프트와 응답(Completion) 전문을 트레이싱 스팬(Span)에 포함시키기 위한 커스텀 ObservationFilter입니다.
 * Spring AI 1.1.0 버전부터는 기본적으로 프롬프트 및 응답 내용이 하이 카디널리티(high-cardinality) 키에 포함되지 않으므로,
 * 이를 명시적으로 추가하여 Langfuse 등에서 확인할 수 있도록 합니다.
 */
public class GenAiContentObservationFilter implements ObservationFilter {

    private static final String KEY_GEN_AI_PROMPT = "gen_ai.prompt";
    private static final String KEY_GEN_AI_COMPLETION = "gen_ai.completion";
    private static final String MASKED_TEXT_PREFIX = "[masked] length=";

    private final boolean captureRawContent;
    private final int maxContentLength;

    public GenAiContentObservationFilter(boolean captureRawContent, int maxContentLength) {
        this.captureRawContent = captureRawContent;
        this.maxContentLength = maxContentLength;
    }

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ChatModelObservationContext chatContext)) {
            return context;
        }

        // 프롬프트 내용 추가
        if (!CollectionUtils.isEmpty(chatContext.getRequest().getInstructions())) {
            String prompt = chatContext.getRequest().getInstructions().stream()
                    .map(Content::getText)
                    .map(this::normalizeContentText)
                    .collect(Collectors.joining("\n"));
            chatContext.addHighCardinalityKeyValue(KeyValue.of(KEY_GEN_AI_PROMPT, resolveObservedContent(prompt)));
        }

        // 응답 내용 추가
        ChatResponse response = chatContext.getResponse();
        if (response != null && !CollectionUtils.isEmpty(response.getResults())) {
            String completion = response.getResults().stream()
                    .map(Generation::getOutput)
                    .map(this::resolveContentText)
                    .collect(Collectors.joining("\n"));
            chatContext.addHighCardinalityKeyValue(
                    KeyValue.of(KEY_GEN_AI_COMPLETION, resolveObservedContent(completion))
            );
        }

        return chatContext;
    }

    private String resolveObservedContent(String content) {
        String safeContent;
        if (content == null) {
            safeContent = "";
        } else {
            safeContent = content;
        }

        if (!captureRawContent) {
            return MASKED_TEXT_PREFIX + safeContent.length();
        }

        if (maxContentLength <= 0) {
            return safeContent;
        }
        if (safeContent.length() <= maxContentLength) {
            return safeContent;
        }

        return safeContent.substring(0, maxContentLength) + "...";
    }

    private String resolveContentText(Content content) {
        if (content == null) {
            return "";
        }

        return normalizeContentText(content.getText());
    }

    private String normalizeContentText(String content) {
        if (content == null) {
            return "";
        }

        return content;
    }
}
