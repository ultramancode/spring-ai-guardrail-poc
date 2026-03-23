package com.ultramancode.aiguardrail.experiment.application.usecase.run.support;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentChatPort;
import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentPiiPort;
import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentTargetPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 실험 케이스 실행(chat/target 분기) 책임을 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperimentCaseExecutionSupport {

    private final ExperimentChatPort experimentChatPort;
    private final ExperimentPiiPort experimentPiiPort;
    private final ExperimentTargetPort experimentTargetPort;

    public CaseExecutionOutput executeCaseByMode(
            RunExperimentCommand command,
            String inputQuestion,
            AttachmentFile file,
            String traceId,
            String systemPrompt
    ) {
        // FULL_WORKFLOW 외 모드(TARGET_ONLY, legacy PROMPT_ONLY)는 target 포트 실행 경로를 사용한다.
        if (command.getMode().isFullWorkflow()) {
            ExperimentChatPort.ExperimentChatResult chatResult = experimentChatPort.chat(
                    new ExperimentChatPort.ExperimentChatRequest(
                            inputQuestion,
                            file,
                            command.getVendor(),
                            command.getModel(),
                            command.getTargetGuardrail(),
                            systemPrompt
                    )
            );
            if (chatResult == null) {
                throw new IllegalStateException("Experiment chat returned null result.");
            }

            String response = chatResult.output();
            if (response == null) {
                throw new IllegalStateException("Experiment chat returned null output.");
            }

            String tokenizedResponse = null;
            try {
                tokenizedResponse = experimentPiiPort.tokenize(response);
            } catch (RuntimeException e) {
                log.warn(
                        "[EXPERIMENT] Failed to tokenize chat response. Fallback to detokenized response. cause={}",
                        e.getMessage(),
                        e
                );
            }

            MaskedResponseResult maskedResponseResult = resolveMaskedResponse(response, tokenizedResponse, "chat");
            return new CaseExecutionOutput(
                    response,
                    maskedResponseResult.maskedResponse(),
                    chatResult.observationId(),
                    maskedResponseResult.fallbackUsed()
            );
        }

        return executeTargetMode(
                command,
                inputQuestion,
                file,
                traceId,
                systemPrompt
        );
    }

    private CaseExecutionOutput executeTargetMode(
            RunExperimentCommand command,
            String inputQuestion,
            AttachmentFile file,
            String traceId,
            String systemPrompt
    ) {
        String targetType = command.getTarget().name();

        ExperimentTargetPort.ExperimentTargetResult targetResult = experimentTargetPort.execute(
                targetType,
                inputQuestion,
                file,
                traceId,
                systemPrompt,
                command.getVendor(),
                command.getModel()
        );
        if (targetResult == null) {
            throw new IllegalStateException("Experiment target execution returned null result.");
        }

        String answer = targetResult.answer();
        if (answer == null) {
            throw new IllegalStateException("Experiment target execution returned null answer.");
        }

        MaskedResponseResult maskedResponseResult = resolveMaskedResponse(
                answer,
                targetResult.maskedAnswer(),
                "target"
        );
        return new CaseExecutionOutput(
                answer,
                maskedResponseResult.maskedResponse(),
                targetResult.observationId(),
                maskedResponseResult.fallbackUsed()
        );
    }

    private MaskedResponseResult resolveMaskedResponse(
            String actualResponse,
            String maskedResponse,
            String sourceType
    ) {
        if (maskedResponse != null) {
            if (!maskedResponse.isBlank()) {
                return new MaskedResponseResult(maskedResponse, false);
            }
        }

        log.warn(
                "[EXPERIMENT] Masked response is null or blank. Fallback to detokenized response. sourceType={}",
                sourceType
        );
        return new MaskedResponseResult(actualResponse, true);
    }

    private record MaskedResponseResult(String maskedResponse, boolean fallbackUsed) {
    }

    public record CaseExecutionOutput(
            String actualResponse,
            String maskedResponse,
            String observationId,
            boolean maskedFallbackUsed
    ) {
    }
}
