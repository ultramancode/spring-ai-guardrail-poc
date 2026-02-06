package com.ultramancode.aiguardrail.multimodal.application.service;

import com.ultramancode.aiguardrail.common.infrastructure.llm.factory.DynamicChatModelFactory;
import com.ultramancode.aiguardrail.common.infrastructure.llm.factory.LlmFactoryRequest;
import com.ultramancode.aiguardrail.multimodal.application.command.MultimodalAnalysisCommand;
import com.ultramancode.aiguardrail.multimodal.application.port.in.AnalyzePdfUseCase;
import com.ultramancode.aiguardrail.multimodal.application.port.out.DocumentParserPort;
import com.ultramancode.aiguardrail.observability.infrastructure.client.LangfuseIngestionClient;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.Observation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzePdfService implements AnalyzePdfUseCase {

    private final DynamicChatModelFactory chatModelFactory;
    private final DocumentParserPort documentParser;
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

        log.info("[VISION] Analyzing PDF: {}", file.getOriginalFilename());

        try {
            // 1. Extract Text
            String extractedText = documentParser.extractText(file.getBytes());

            // 2. Real-time PII Masking (Input)
            String maskedQuestion = piiService.tokenize(question);
            String maskedExtractedText = piiService.tokenize(extractedText);

            // 3. Ask LLM with Masked Content
            ChatClient chatClient = chatModelFactory.createChatClient(
                    LlmFactoryRequest.builder().vendor("gemini").build()
            );

            var chatResponse = chatClient.prompt()
                    .user(u -> u.text(maskedQuestion + "\n\n[Context]\n" + maskedExtractedText))
                    .call()
                    .chatResponse();

            String maskedAnswer = chatResponse.getResult().getOutput().getText();

            // 4. Real-time PII Unmasking (Output for User)
            String finalAnswer = piiService.detokenize(maskedAnswer);

            // 4. Trace enrichment and Conditional Manual Recording
            Observation currentObs = observationRegistry.getCurrentObservation();
            if (currentObs != null) {
                currentObs.highCardinalityKeyValue("input", question);
                currentObs.highCardinalityKeyValue("output", finalAnswer);
            }

            // Manual recording is only for NON-experiment flows (regular API calls)
            // Experiments already have the media in Langfuse and use a dedicated workflow grouping.
            if (!command.isExperiment()) {
                log.debug("[VISION] Manual recording to Langfuse for regular API trace: {}", traceId);
                try {
                    langfuseIngestionClient.recordGenerationWithAttachment(
                            traceId,
                            "PDF_PARSED",
                            modelName,
                            file.getOriginalFilename(),
                            file.getBytes(),
                            maskedQuestion,
                            maskedExtractedText,
                            maskedAnswer,
                            startTime,
                            System.currentTimeMillis()
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
                    .processingMode("PDF_PARSED")
                    .originalFileName(file.getOriginalFilename())
                    .extractedText(extractedText) // Postman extracted text also raw (optional, but keep for demo)
                    .build();

        } catch (Exception e) {
            log.error("[VISION] PDF Analysis failed", e);
            throw new RuntimeException("PDF analysis failed: " + e.getMessage());
        }
    }
}
