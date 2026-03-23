package com.ultramancode.aiguardrail.guardrail.application.support;

import com.ultramancode.aiguardrail.guardrail.domain.PiiContext;
import com.ultramancode.aiguardrail.guardrail.domain.PiiSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PII 스팬을 토큰 문자열로 치환하는 책임을 담당합니다.
 */
@Slf4j
@Component
public class PiiTokenReplacementSupport {

    public String apply(String text, List<PiiSpan> spans, PiiContext context) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (spans == null || spans.isEmpty()) {
            return text;
        }

        List<PiiSpan> descendingSpans = new ArrayList<>(spans);
        descendingSpans.sort(Comparator.comparingInt(PiiSpan::start).reversed());

        StringBuilder builder = new StringBuilder(text);
        for (PiiSpan span : descendingSpans) {
            if (!isValidSpan(span, text.length())) {
                log.warn(
                        "[PII] Skip invalid span: type={}, start={}, end={}, textLength={}",
                        span.type(),
                        span.start(),
                        span.end(),
                        text.length()
                );
                continue;
            }

            String original = text.substring(span.start(), span.end());
            String token = context.getOrCreateToken(original, span.type());
            builder.replace(span.start(), span.end(), token);
        }

        return builder.toString();
    }

    private boolean isValidSpan(PiiSpan span, int textLength) {
        if (span == null) {
            return false;
        }
        if (span.start() < 0) {
            return false;
        }
        if (span.end() > textLength) {
            return false;
        }
        if (span.start() >= span.end()) {
            return false;
        }

        return true;
    }
}
