package com.ultramancode.aiguardrail.prompt.infrastructure.adapter;

import com.ultramancode.aiguardrail.prompt.application.port.out.PromptPort;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptTemplate;
import com.ultramancode.aiguardrail.prompt.domain.FetchedPrompt;
import com.ultramancode.aiguardrail.prompt.infrastructure.component.LangfusePromptFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Langfuse를 이용한 PromptPort 구현체
 */
@Component
@RequiredArgsConstructor
public class LangfusePromptAdapter implements PromptPort {

    private final LangfusePromptFetcher promptFetcher;

    @Override
    public Optional<PromptTemplate> fetchPrompt(String promptName) {
        return promptFetcher.fetch(promptName)
                .map(this::toTemplate);
    }

    @Override
    public PromptTemplate fetchPromptOrThrow(String promptName) {
        return toTemplate(promptFetcher.fetchOrThrow(promptName));
    }

    private PromptTemplate toTemplate(FetchedPrompt prompt) {
        return new PromptTemplate(prompt.content(), prompt.name(), prompt.version());
    }
}
