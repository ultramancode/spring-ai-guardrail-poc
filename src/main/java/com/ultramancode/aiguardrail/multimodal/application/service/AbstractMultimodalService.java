package com.ultramancode.aiguardrail.multimodal.application.service;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import com.ultramancode.aiguardrail.common.llm.application.port.out.LlmPort;
import com.ultramancode.aiguardrail.common.observability.GenerationRecordResult;
import com.ultramancode.aiguardrail.common.observability.ObservabilityTags;
import com.ultramancode.aiguardrail.common.observability.domain.GenerationAttachment;
import com.ultramancode.aiguardrail.common.observability.port.out.ObservabilityPort;
import com.ultramancode.aiguardrail.common.observability.support.GenerationRecordSupport;
import com.ultramancode.aiguardrail.common.pii.port.out.PiiProcessingPort;
import com.ultramancode.aiguardrail.common.util.TraceContentPolicy;
import com.ultramancode.aiguardrail.multimodal.application.command.MultimodalAnalysisCommand;
import com.ultramancode.aiguardrail.multimodal.application.model.LlmExecutionData;
import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractMultimodalService {

    protected final LlmPort llmPort;
    protected final ObservabilityPort observabilityPort;
    protected final PiiProcessingPort piiProcessingPort;
    protected final ObservationRegistry observationRegistry;

    @Value("${guardrail.pii.trace-raw-content:false}")
    private boolean traceRawContent;

    /**
     * 멀티모달 분석 공통 파이프라인을 실행한다.
     * 파일 읽기, PII 토큰화, LLM 호출, 관측 기록을 포함한다.
     */
    protected MultimodalAnalysisResult runAnalysisPipeline(
            MultimodalAnalysisCommand command,
            String analysisMode,
            String generationName
    ) {
        long startTime = System.currentTimeMillis();
        AttachmentFile file = command.getFile();
        String question = command.getQuestion();
        String traceId = command.getTraceId();

        try {
            String maskedQuestion = piiProcessingPort.tokenize(question);
            LlmExecutionData llmExecutionData = prepare(command, maskedQuestion);

            ChatResponse chatResponse = executeLlm(llmExecutionData);
            String maskedAnswer = resolveMaskedAnswer(chatResponse, analysisMode);

            String finalAnswer = piiProcessingPort.detokenize(maskedAnswer);

            enrichObservation(question, finalAnswer);

            GenerationRecordResult generationRecordResult = recordToObservability(
                    traceId,
                    generationName,
                    maskedQuestion,
                    maskedAnswer,
                    startTime,
                    llmExecutionData.attachment(),
                    llmExecutionData.vendor(),
                    llmExecutionData.model()
            );

            return buildResult(
                    finalAnswer,
                    maskedAnswer,
                    traceId,
                    analysisMode,
                    file.getOriginalFilename(),
                    generationRecordResult.observationId()
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            if (e instanceof IOException) {
                log.error("[VISION] {} I/O failed", analysisMode, e);
            } else {
                log.error("[VISION] {} failed", analysisMode, e);
            }
            throw new IllegalStateException(analysisMode + " failed", e);
        }
    }

    private String resolveMaskedAnswer(ChatResponse chatResponse, String analysisMode) {
        if (chatResponse == null) {
            throw new IllegalStateException(analysisMode + " failed: chat response is null");
        }

        Generation generation = chatResponse.getResult();
        if (generation == null || generation.getOutput() == null) {
            throw new IllegalStateException(analysisMode + " failed: chat response output is empty");
        }

        String outputText = generation.getOutput().getText();
        if (outputText == null) {
            throw new IllegalStateException(analysisMode + " failed: chat response text is null");
        }

        return outputText;
    }

    protected abstract LlmExecutionData prepare(MultimodalAnalysisCommand command, String maskedQuestion) throws IOException;

    protected abstract ChatResponse executeLlm(LlmExecutionData context);

    protected ChatResponse executeDefaultTextLlm(String prompt, String systemPrompt, String vendor, String model) {
        ChatClient chatClient = llmPort.getChatClient(vendor, model);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .chatResponse();
        }

        return chatClient.prompt()
                .user(prompt)
                .call()
                .chatResponse();
    }

    protected ChatResponse executeDefaultMediaLlm(
            String prompt,
            Resource mediaSource,
            String mediaType,
            String systemPrompt,
            String vendor,
            String model
    ) {
        ChatClient chatClient = llmPort.getChatClient(vendor, model);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(u -> u.text(prompt)
                            .media(MimeTypeUtils.parseMimeType(mediaType), mediaSource))
                    .call()
                    .chatResponse();
        }

        return chatClient.prompt()
                .user(u -> u.text(prompt)
                        .media(MimeTypeUtils.parseMimeType(mediaType), mediaSource))
                .call()
                .chatResponse();
    }

    protected void enrichObservation(String input, String output) {
        Observation currentObs = observationRegistry.getCurrentObservation();
        if (currentObs != null) {
            currentObs.highCardinalityKeyValue(ObservabilityTags.KEY_INPUT, resolveTraceContent(input));
            currentObs.highCardinalityKeyValue(ObservabilityTags.KEY_OUTPUT, resolveTraceContent(output));
        }
    }

    private String resolveTraceContent(String rawContent) {
        return TraceContentPolicy.resolve(rawContent, traceRawContent, piiProcessingPort::tokenize);
    }

    private GenerationRecordResult recordToObservability(
            String traceId,
            String operationName,
            String maskedQuestion,
            String maskedAnswer,
            long startTime,
            GenerationAttachment attachment,
            String vendor,
            String model
    ) {
        return GenerationRecordSupport.recordGenerationSafely(
                observabilityPort,
                llmPort,
                traceId,
                operationName,
                vendor,
                model,
                maskedQuestion,
                maskedAnswer,
                startTime,
                attachment,
                "[VISION]"
        );
    }

    private MultimodalAnalysisResult buildResult(
            String answer,
            String maskedAnswer,
            String traceId,
            String mode,
            String fileName,
            String observationId
    ) {
        return MultimodalAnalysisResult.builder()
                .answer(answer)
                .maskedAnswer(maskedAnswer)
                .traceId(traceId)
                .observationId(observationId)
                .processingMode(mode)
                .originalFileName(fileName)
                .build();
    }
}
