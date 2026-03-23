package com.ultramancode.aiguardrail.guardrail.infrastructure.support;

import com.ultramancode.aiguardrail.common.util.ErrorMessageResolver;
import com.ultramancode.aiguardrail.guardrail.application.exception.GuardrailDetectorUnavailableException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChatHandlerExceptionSupport {

    public static RuntimeException rethrow(RuntimeException exception, String fallbackMessage) {
        if (exception instanceof SecurityException) {
            return exception;
        }
        if (exception instanceof GuardrailDetectorUnavailableException) {
            return exception;
        }
        if (exception instanceof IllegalArgumentException) {
            return exception;
        }
        if (exception instanceof IllegalStateException) {
            return exception;
        }

        String resolvedCause = ErrorMessageResolver.resolve(exception, exception.getClass().getSimpleName());
        return new IllegalStateException(fallbackMessage + ". cause=" + resolvedCause, exception);
    }
}
