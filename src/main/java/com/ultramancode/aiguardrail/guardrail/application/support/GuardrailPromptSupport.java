package com.ultramancode.aiguardrail.guardrail.application.support;

import com.ultramancode.aiguardrail.prompt.application.exception.PromptFetchFailedException;
import com.ultramancode.aiguardrail.prompt.application.exception.PromptNotFoundException;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptPort;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GuardrailPromptSupport {

    public static String fetchPromptContentOrFallback(
            PromptPort promptPort,
            String promptName,
            String fallbackPrompt,
            String logPrefix,
            String promptPurpose,
            Logger log
    ) {
        try {
            return promptPort.fetchPromptOrThrow(promptName).content();
        } catch (PromptNotFoundException | PromptFetchFailedException e) {
            log.warn(
                    "{} Failed to load {} prompt '{}'. Using fallback. cause={}",
                    logPrefix,
                    promptPurpose,
                    promptName,
                    e.getMessage(),
                    e
            );
            return fallbackPrompt;
        }
    }
}

