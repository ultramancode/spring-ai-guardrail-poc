package com.ultramancode.aiguardrail.common.llm.infrastructure.factory.provider;

import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 공통 로직을 담은 추상 LLM Provider
 */
@RequiredArgsConstructor
public abstract class AbstractLlmProvider implements LlmProvider {

    private static final String PREFIX_HTTP = "http://";
    private static final String PREFIX_HTTPS = "https://";

    protected final ObservationRegistry observationRegistry;

    /**
     * 요청값이 유효하면 요청값을 반환하고, 없으면 설정 기본값(propertyDefault)을 반환합니다.
     */
    protected String getOrDefault(String requested, String propertyDefault) {
        return StringUtils.hasText(requested) ? requested : propertyDefault;
    }

    /**
     * 요청값이 있으면 요청값을 반환하고, 없으면 설정 기본값(propertyDefault)을 반환합니다.
     */
    protected Double getOrDefault(Double requested, Double propertyDefault) {
        return requested != null ? requested : propertyDefault;
    }

    /**
     * URL을 정규화합니다 (http:// 프리픽스 추가)
     */
    protected String normalizeUrl(String url, String defaultUrl) {
        if (url == null) {
            return defaultUrl;
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith(PREFIX_HTTP) && !trimmed.startsWith(PREFIX_HTTPS)) {
            return PREFIX_HTTP + trimmed;
        }
        return trimmed;
    }

    @Override
    public String getDefaultModelName() {
        return getPropertyModel();
    }

    protected abstract String getPropertyModel();

}
