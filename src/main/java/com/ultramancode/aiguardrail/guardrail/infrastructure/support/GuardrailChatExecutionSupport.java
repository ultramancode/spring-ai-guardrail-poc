package com.ultramancode.aiguardrail.guardrail.infrastructure.support;

import com.ultramancode.aiguardrail.common.observability.GenerationRecordResult;
import com.ultramancode.aiguardrail.common.observability.TraceIdResolver;
import com.ultramancode.aiguardrail.common.observability.domain.AnalysisMode;
import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.guardrail.application.result.PiiChatResult;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GuardrailChatExecutionSupport {

    private final List<ToolCallback> securedToolCallbacks;
    private final GuardrailAdvisorChainFactory guardrailAdvisorChainFactory;
    private final GuardrailGenerationRecorder generationRecorder;
    private final PiiUseCase piiService;

    @Value("${guardrail.pii.trace-raw-content:false}")
    private boolean traceRawContent;

    public PiiChatResult executeText(
            ChatClient client,
            PiiChatCommand command,
            PromptTemplate prompt,
            String userInput,
            AnalysisMode analysisMode,
            @Nullable String extractedText
    ) {
        long startTime = System.currentTimeMillis();
        String finalContent = client.prompt()
                .system(prompt.content())
                .user(userInput)
                .advisors(guardrailAdvisorChainFactory.build(command.getTargetGuardrail()))
                .toolCallbacks(resolveToolCallbacks())
                .call()
                .content();
        validateFinalContent(finalContent, analysisMode);

        return buildResult(command, analysisMode, finalContent, startTime, extractedText);
    }

    public PiiChatResult executeImage(
            ChatClient client,
            PiiChatCommand command,
            PromptTemplate prompt,
            String userInput,
            MimeType mimeType,
            Resource mediaResource,
            AnalysisMode analysisMode,
            @Nullable String extractedText
    ) {
        long startTime = System.currentTimeMillis();
        String finalContent = client.prompt()
                .system(prompt.content())
                .user(user -> user.text(userInput).media(mimeType, mediaResource))
                .advisors(guardrailAdvisorChainFactory.build(command.getTargetGuardrail()))
                .toolCallbacks(resolveToolCallbacks())
                .call()
                .content();
        validateFinalContent(finalContent, analysisMode);

        return buildResult(command, analysisMode, finalContent, startTime, extractedText);
    }

    private PiiChatResult buildResult(
            PiiChatCommand command,
            AnalysisMode analysisMode,
            String finalContent,
            long startTime,
            @Nullable String extractedText
    ) {
        GenerationRecordResult generationRecord = generationRecorder.recordFromCommand(
                TraceIdResolver.currentTraceIdOrNull(),
                analysisMode.getValue(),
                command,
                finalContent,
                startTime,
                traceRawContent,
                piiService,
                extractedText
        );

        return PiiChatResult.of(finalContent, generationRecord.observationId());
    }

    private ToolCallback[] resolveToolCallbacks() {
        return securedToolCallbacks.toArray(ToolCallback[]::new);
    }

    private void validateFinalContent(String finalContent, AnalysisMode analysisMode) {
        if (finalContent == null) {
            throw new IllegalStateException("LLM returned null output. mode=" + analysisMode.getValue());
        }
    }
}

