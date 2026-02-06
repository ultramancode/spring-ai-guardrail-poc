package com.ultramancode.aiguardrail.multimodal.application.service;

import com.ultramancode.aiguardrail.common.infrastructure.llm.factory.DynamicChatModelFactory;
import com.ultramancode.aiguardrail.common.infrastructure.llm.factory.LlmFactoryRequest;
import com.ultramancode.aiguardrail.multimodal.application.command.MultimodalAnalysisCommand;
import com.ultramancode.aiguardrail.multimodal.application.port.in.AnalyzeImageUseCase;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.observability.infrastructure.client.LangfuseIngestionClient;
import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.Observation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 분석 서비스
 * <p>
 * [VISION GUARDRAIL 분석]
 * 실무에서 이미지 내 개인정보(PII)를 보호하려면 본 서비스 호출 전 다음 중 하나를 수행해야 합니다:
 * 1. Redaction 모델 적용: 얼굴이나 텍스트 영역을 검은 박스로 가린(Masked) 이미지를 생성하여 전달.
 * 2. OCR-Base 가드레일: 이미지에서 텍스트를 먼저 추출하고, 추출된 텍스트를 토큰화하여 LLM에 전달 (PDF 분석 방식과 동일).
 * <p>
 * 본 PoC에서는 질문(Question)과 응답(Answer)에 대한 실시간 텍스트 가드레일 흐름을 보여줍니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzeImageService implements AnalyzeImageUseCase {

    private final DynamicChatModelFactory chatModelFactory;
    private final LangfuseIngestionClient langfuseIngestionClient;
    private final PiiUseCase piiService;
    private final ObservationRegistry observationRegistry;

    @Value("${spring.ai.google.genai.chat.options.model}")
    private String modelName;

    @Value("${guardrail.pii.trace-raw-content:false}")
    private boolean traceRawContent;

    public MultimodalAnalysisResult execute(MultimodalAnalysisCommand command) {
        long startTime = System.currentTimeMillis();
        MultipartFile file = command.getFile();
        String question = command.getQuestion();
        String traceId = command.getTraceId();

        log.info("[VISION] Analyzing image: {}", file.getOriginalFilename());

        try {
            // 1. Real-time PII Masking (Input)
            String maskedQuestion = piiService.tokenize(question);
            byte[] imageBytes = file.getBytes();

            // 2. Ask LLM with Masked Question
            ChatClient chatClient = chatModelFactory.createChatClient(
                    LlmFactoryRequest.builder().vendor("gemini").build()
            );
            var chatResponse = chatClient.prompt()
                    .user(u -> u.text(maskedQuestion)
                            .media(MimeTypeUtils.parseMimeType(file.getContentType()), new ByteArrayResource(imageBytes)))
                    .call()
                    .chatResponse();

            String maskedAnswer = chatResponse.getResult().getOutput().getText();

            // 3. Real-time PII Unmasking (Output for User)
            String finalAnswer = piiService.detokenize(maskedAnswer);

            // 3. Trace enrichment and Conditional Manual Recording
            Observation currentObs = observationRegistry.getCurrentObservation();
            if (currentObs != null) {
                currentObs.highCardinalityKeyValue("input", question);
                currentObs.highCardinalityKeyValue("output", finalAnswer);
            }

            // Manual recording is only for NON-experiment flows (regular API calls)
            if (!command.isExperiment()) {
                log.debug("[VISION] Manual recording to Langfuse for regular API trace: {}", traceId);
                try {
                    langfuseIngestionClient.recordVisionGenerationWithMedia(
                            traceId,
                            modelName,
                            imageBytes,
                            file.getContentType(),
                            maskedQuestion,
                            maskedAnswer,
                            startTime,
                            System.currentTimeMillis(),
                            null
                    );
                } catch (Exception re) {
                    log.warn("[VISION] Failed to record manual Langfuse log", re);
                }
            } else {
                log.debug("[VISION] Skipping manual recording for Trace: {} (Experiment mode)", traceId);
            }

            return MultimodalAnalysisResult.builder()
                    .answer(finalAnswer)
                    .maskedAnswer(maskedAnswer)
                    .traceId(traceId)
                    .processingMode("DIRECT_VISION")
                    .originalFileName(file.getOriginalFilename())
                    .build();

        } catch (Exception e) {
            log.error("[VISION] Analysis failed", e);
            throw new RuntimeException("Image analysis failed: " + e.getMessage());
        }
    }
}
