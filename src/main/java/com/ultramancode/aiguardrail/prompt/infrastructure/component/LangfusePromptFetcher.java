package com.ultramancode.aiguardrail.prompt.infrastructure.component;

import com.langfuse.client.LangfuseClient;
import com.langfuse.client.core.LangfuseClientApiException;
import com.langfuse.client.resources.prompts.requests.GetPromptRequest;
import com.langfuse.client.resources.prompts.types.Prompt;
import com.langfuse.client.resources.prompts.types.TextPrompt;
import com.ultramancode.aiguardrail.prompt.application.exception.PromptFetchFailedException;
import com.ultramancode.aiguardrail.prompt.application.exception.PromptNotFoundException;
import com.ultramancode.aiguardrail.prompt.domain.FetchedPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Langfuse SDK를 이용한 프롬프트 조회 컴포넌트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangfusePromptFetcher {

    private final LangfuseClient langfuseClient;
    @Value("${langfuse.prompt-label:production}")
    private String promptLabel;

    public Optional<FetchedPrompt> fetch(String promptName) {
        try {
            return Optional.of(fetchOrThrow(promptName));
        } catch (PromptNotFoundException e) {
            log.warn("[PROMPT] Prompt '{}' not found: {}", promptName, e.getMessage(), e);
        } catch (PromptFetchFailedException e) {
            log.warn("[PROMPT] Failed to fetch prompt '{}': {}", promptName, e.getMessage(), e);
        }
        return Optional.empty();
    }

    public FetchedPrompt fetchOrThrow(String promptName) {
        try {
            Prompt prompt = fetchPromptByLabel(promptName);
            if (prompt.isText() && prompt.getText().isPresent()) {
                TextPrompt textPrompt = prompt.getText().get();
                log.info("[PROMPT] Successfully fetched prompt '{}' from Langfuse (v{})",
                        promptName, textPrompt.getVersion());
                return new FetchedPrompt(
                        textPrompt.getPrompt().stripIndent(),
                        promptName,
                        textPrompt.getVersion()
                );
            }
            throw new PromptFetchFailedException("Prompt exists but is not a text prompt: " + promptName);
        } catch (PromptNotFoundException | PromptFetchFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            if (isPromptNotFoundError(e)) {
                throw new PromptNotFoundException("Prompt not found: " + promptName, e);
            }
            throw new PromptFetchFailedException("Failed to fetch prompt from Langfuse: " + promptName, e);
        }
    }

    private boolean isPromptNotFoundError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof LangfuseClientApiException apiException) {
                if (apiException.statusCode() == 404) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private Prompt fetchPromptByLabel(String promptName) {
        String normalizedLabel = normalizePromptLabel(promptLabel);
        if (normalizedLabel == null) {
            return langfuseClient.prompts().get(promptName);
        }

        GetPromptRequest request = GetPromptRequest.builder()
                .label(normalizedLabel)
                .build();

        return langfuseClient.prompts().get(promptName, request);
    }

    private String normalizePromptLabel(String label) {
        if (label == null) {
            return null;
        }

        String normalizedLabel = label.trim();
        if (normalizedLabel.isEmpty()) {
            return null;
        }

        return normalizedLabel;
    }
}
