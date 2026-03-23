package com.ultramancode.aiguardrail.guardrail.application.service;

import com.ultramancode.aiguardrail.common.observability.LangfuseConstants;
import com.ultramancode.aiguardrail.common.observability.domain.ObservationType;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.guardrail.application.port.out.PiiAnalyzerPort;
import com.ultramancode.aiguardrail.guardrail.application.support.PiiDetokenizationSupport;
import com.ultramancode.aiguardrail.guardrail.application.support.PiiSpanDeduplicationSupport;
import com.ultramancode.aiguardrail.guardrail.application.support.PiiTokenReplacementSupport;
import com.ultramancode.aiguardrail.guardrail.domain.PiiContext;
import com.ultramancode.aiguardrail.guardrail.domain.PiiContextStore;
import com.ultramancode.aiguardrail.guardrail.domain.PiiSpan;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * PII 토큰화/복원 기능을 담당하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PiiService implements PiiUseCase {

    private final List<PiiAnalyzerPort> piiAnalyzers;
    private final ObservationRegistry observationRegistry;
    private final PiiContextStore piiContextStore;
    private final PiiSpanDeduplicationSupport piiSpanDeduplicationSupport;
    private final PiiTokenReplacementSupport piiTokenReplacementSupport;
    private final PiiDetokenizationSupport piiDetokenizationSupport;

    public String tokenize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        // 동일 입력은 컨텍스트 캐시를 우선 사용합니다.
        PiiContext context = piiContextStore.get();
        if (context.getAnalysisCache().containsKey(text)) {
            log.debug("[PII-CACHE] Cache hit (No span created) for text: '{}'",
                    text.length() > 20 ? text.substring(0, 20) + "..." : text);
            return context.getAnalysisCache().get(text);
        }

        return Observation.createNotStarted("pii.tokenize", observationRegistry)
                .lowCardinalityKeyValue(LangfuseConstants.TAG_OBSERVATION_TYPE, ObservationType.GUARDRAIL.getValue())
                .observe(() -> tokenizeWithContext(text, context));
    }

    /**
     * 내부 토큰화 API입니다. 별도 Observation 생성 없이 동작합니다.
     */
    public String tokenizeWithoutObservation(String text) {
        return tokenizeWithContext(text, piiContextStore.get());
    }

    private String tokenizeWithContext(String text, PiiContext context) {
        if (text == null || text.isBlank()) {
            return text;
        }

        if (context.getAnalysisCache().containsKey(text)) {
            return context.getAnalysisCache().get(text);
        }

        List<PiiSpan> allSpans = new ArrayList<>();
        for (PiiAnalyzerPort analyzer : piiAnalyzers) {
            List<PiiSpan> analyzedSpans = analyzer.analyze(text);
            if (analyzedSpans == null || analyzedSpans.isEmpty()) {
                continue;
            }
            allSpans.addAll(analyzedSpans);
        }

        List<PiiSpan> filteredSpans = piiSpanDeduplicationSupport.deduplicate(allSpans);
        String result = piiTokenReplacementSupport.apply(text, filteredSpans, context);
        context.getAnalysisCache().put(text, result);
        return result;
    }

    public String detokenize(String text) {
        return Observation.createNotStarted("pii.detokenize", observationRegistry)
                .highCardinalityKeyValue(LangfuseConstants.TAG_OBSERVATION_TYPE, ObservationType.GUARDRAIL.getValue())
                .observe(() -> detokenizeWithoutObservation(text));
    }

    public String detokenizeWithoutObservation(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        PiiContext context = piiContextStore.get();
        return piiDetokenizationSupport.detokenizeText(text, context.getTokenToOriginal());
    }

    public Object detokenizeRecursive(Object input) {
        return piiDetokenizationSupport.detokenizeRecursive(input, this::detokenizeWithoutObservation);
    }

    @Override
    public void clearContext() {
        piiContextStore.clear();
    }
}
