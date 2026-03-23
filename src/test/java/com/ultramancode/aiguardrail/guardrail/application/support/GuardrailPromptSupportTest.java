package com.ultramancode.aiguardrail.guardrail.application.support;

import com.ultramancode.aiguardrail.prompt.application.exception.PromptFetchFailedException;
import com.ultramancode.aiguardrail.prompt.application.exception.PromptNotFoundException;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptPort;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptTemplate;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuardrailPromptSupportTest {

    @Test
    void fetchPromptContentOrFallback_returnsPromptContent_whenFetchSucceeds() {
        PromptPort promptPort = mock(PromptPort.class);
        Logger logger = mock(Logger.class);
        when(promptPort.fetchPromptOrThrow("prompt-a"))
                .thenReturn(new PromptTemplate("resolved-content", "prompt-a", 1));

        String result = GuardrailPromptSupport.fetchPromptContentOrFallback(
                promptPort,
                "prompt-a",
                "fallback-content",
                "[TEST]",
                "system",
                logger
        );

        assertEquals("resolved-content", result);
    }

    @Test
    void fetchPromptContentOrFallback_returnsFallback_whenPromptNotFound() {
        PromptPort promptPort = mock(PromptPort.class);
        Logger logger = mock(Logger.class);
        when(promptPort.fetchPromptOrThrow("prompt-a"))
                .thenThrow(new PromptNotFoundException("not found"));

        String result = GuardrailPromptSupport.fetchPromptContentOrFallback(
                promptPort,
                "prompt-a",
                "fallback-content",
                "[TEST]",
                "system",
                logger
        );

        assertEquals("fallback-content", result);
    }

    @Test
    void fetchPromptContentOrFallback_returnsFallback_whenPromptFetchFails() {
        PromptPort promptPort = mock(PromptPort.class);
        Logger logger = mock(Logger.class);
        when(promptPort.fetchPromptOrThrow("prompt-a"))
                .thenThrow(new PromptFetchFailedException("temporary failure"));

        String result = GuardrailPromptSupport.fetchPromptContentOrFallback(
                promptPort,
                "prompt-a",
                "fallback-content",
                "[TEST]",
                "system",
                logger
        );

        assertEquals("fallback-content", result);
    }

    @Test
    void fetchPromptContentOrFallback_rethrowsException_whenUnexpectedRuntimeExceptionOccurs() {
        PromptPort promptPort = mock(PromptPort.class);
        Logger logger = mock(Logger.class);
        when(promptPort.fetchPromptOrThrow("prompt-a"))
                .thenThrow(new IllegalStateException("unexpected"));

        assertThrows(
                IllegalStateException.class,
                () -> GuardrailPromptSupport.fetchPromptContentOrFallback(
                        promptPort,
                        "prompt-a",
                        "fallback-content",
                        "[TEST]",
                        "system",
                        logger
                )
        );
    }
}
