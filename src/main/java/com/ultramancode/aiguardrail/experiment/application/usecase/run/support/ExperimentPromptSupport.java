package com.ultramancode.aiguardrail.experiment.application.usecase.run.support;

import com.ultramancode.aiguardrail.common.util.ErrorMessageResolver;
import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExperimentPromptSupport {

    private final PromptPort promptPort;

    public ResolvedPrompt resolvePromptOnce(RunExperimentCommand.PromptConfig promptConfig) {
        try {
            return new ResolvedPrompt(resolveSystemPrompt(promptConfig), null);
        } catch (RuntimeException e) {
            String errorMessage = ErrorMessageResolver.resolve(e, "PromptResolutionException");
            log.error("[EXPERIMENT] Failed to resolve system prompt once: {}", errorMessage, e);
            return new ResolvedPrompt(null, errorMessage);
        }
    }

    public String buildPromptInfo(RunExperimentCommand command) {
        RunExperimentCommand.PromptConfig promptConfig = command.getPrompt();
        String promptInfo = null;

        if (promptConfig != null) {
            String name = promptConfig.getName();
            String version = promptConfig.getVersion();
            String systemPrompt = promptConfig.getSystemPrompt();

            if (name != null) {
                if (version != null) {
                    promptInfo = name + "@" + version;
                } else {
                    promptInfo = name;
                }
            } else if (systemPrompt != null && !systemPrompt.isBlank()) {
                promptInfo = "inline-system-prompt";
            }
        }

        String targetGuardrail = command.getTargetGuardrail();
        if (targetGuardrail == null || targetGuardrail.isBlank()) {
            return promptInfo;
        }
        if (promptInfo == null || promptInfo.isBlank()) {
            return "guardrail=" + targetGuardrail;
        }

        return promptInfo + " | guardrail=" + targetGuardrail;
    }

    private String resolveSystemPrompt(RunExperimentCommand.PromptConfig promptConfig) {
        if (promptConfig == null) {
            return null;
        }

        String inlineSystemPrompt = promptConfig.getSystemPrompt();
        if (inlineSystemPrompt != null && !inlineSystemPrompt.isBlank()) {
            return inlineSystemPrompt;
        }

        String promptName = promptConfig.getName();
        if (promptName == null || promptName.isBlank()) {
            return null;
        }

        // POC 정책: version은 실행 메타정보 용도이며, 실제 조회는 최신(name 기준)으로 고정한다.
        return promptPort.fetchPromptOrThrow(promptName).content();
    }

    public record ResolvedPrompt(String systemPrompt, String errorMessage) {
    }
}

