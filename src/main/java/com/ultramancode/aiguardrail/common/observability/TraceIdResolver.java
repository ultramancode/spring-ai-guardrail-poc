package com.ultramancode.aiguardrail.common.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 현재 스팬/컨텍스트에서 사용할 traceId를 안전하게 추출하는 유틸리티입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TraceIdResolver {

    public static String currentTraceIdOrNull() {
        return fromSpanContext(Span.current().getSpanContext());
    }

    public static String fromSpanContext(SpanContext spanContext) {
        if (spanContext == null) {
            return null;
        }

        String traceId = spanContext.getTraceId();
        if (!TraceUtils.isValid(traceId)) {
            return null;
        }

        return traceId;
    }
}
