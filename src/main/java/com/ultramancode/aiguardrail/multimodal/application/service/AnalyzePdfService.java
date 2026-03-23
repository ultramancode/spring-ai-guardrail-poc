package com.ultramancode.aiguardrail.multimodal.application.service;

import com.ultramancode.aiguardrail.common.document.port.out.DocumentParserPort;
import com.ultramancode.aiguardrail.common.llm.application.port.out.LlmPort;
import com.ultramancode.aiguardrail.common.observability.domain.AnalysisMode;
import com.ultramancode.aiguardrail.common.observability.domain.GenerationAttachment;
import com.ultramancode.aiguardrail.common.observability.port.out.ObservabilityPort;
import com.ultramancode.aiguardrail.common.pii.port.out.PiiProcessingPort;
import com.ultramancode.aiguardrail.common.util.MediaProcessingUtils;
import com.ultramancode.aiguardrail.common.util.MediaTypePolicy;
import com.ultramancode.aiguardrail.common.util.MultimodalValidationUtils;
import com.ultramancode.aiguardrail.multimodal.application.command.MultimodalAnalysisCommand;
import com.ultramancode.aiguardrail.multimodal.application.model.LlmExecutionData;
import com.ultramancode.aiguardrail.multimodal.application.port.in.AnalyzePdfUseCase;
import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;
import com.ultramancode.aiguardrail.multimodal.domain.MultimodalTargetType;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * PDF 파일 분석 요청을 처리한다.
 */
@Slf4j
@Service
public class AnalyzePdfService extends AbstractMultimodalService implements AnalyzePdfUseCase {

    private static final String PROMPT_TAG_CONTEXT = "[Context]";

    private final DocumentParserPort documentParser;

    @Value("${guardrail.pdf.on-extract-fail:llm_notice}")
    private String onPdfExtractFail;

    @Value("${guardrail.pdf.max-extracted-chars:20000}")
    private int maxPdfExtractChars;

    @Value("${guardrail.multimodal.max-file-size-bytes:10485760}")
    private long maxMultimodalFileSizeBytes;

    public AnalyzePdfService(
            LlmPort llmPort,
            DocumentParserPort documentParser,
            ObservabilityPort observabilityPort,
            PiiProcessingPort piiProcessingPort,
            ObservationRegistry observationRegistry
    ) {
        super(llmPort, observabilityPort, piiProcessingPort, observationRegistry);
        this.documentParser = documentParser;
    }

    @Override
    public MultimodalAnalysisResult analyze(MultimodalAnalysisCommand command) {
        log.info("[VISION] Analyzing PDF: {}", command.getFile().getOriginalFilename());
        return super.runAnalysisPipeline(
                command,
                AnalysisMode.ANALYZE_PDF.getValue(),
                AnalysisMode.ANALYZE_PDF.getValue()
        );
    }

    @Override
    protected LlmExecutionData prepare(MultimodalAnalysisCommand command, String maskedQuestion) {
        String pdfContentType = validatePdfContentType(command.getFile().getContentType());
        byte[] fileBytes = command.getFile().getBytes();
        MultimodalValidationUtils.validateFileSize(
                fileBytes.length,
                command.getFile().getOriginalFilename(),
                maxMultimodalFileSizeBytes,
                "pdf"
        );
        MediaProcessingUtils.PdfProcessingResult pdfProcessingResult = MediaProcessingUtils.extractMaskedPdfText(
                fileBytes,
                onPdfExtractFail,
                documentParser::extractText,
                piiProcessingPort::tokenize,
                message -> log.warn("[VISION] {}", message)
        );
        String maskedExtractedText = MultimodalValidationUtils.truncateText(
                pdfProcessingResult.maskedExtractedText(),
                maxPdfExtractChars,
                "PDF 추출 텍스트",
                message -> log.warn("[VISION] {}", message)
        );

        return LlmExecutionData.builder()
                .prompt(maskedQuestion + "\n\n" + PROMPT_TAG_CONTEXT + "\n" + maskedExtractedText)
                .systemPrompt(command.getSystemPrompt())
                .vendor(command.getVendor())
                .model(command.getModel())
                .attachment(new GenerationAttachment(
                        command.getFile().getOriginalFilename(),
                        pdfContentType,
                        fileBytes,
                        maskedExtractedText
                ))
                .build();
    }

    @Override
    protected ChatResponse executeLlm(LlmExecutionData context) {
        return executeDefaultTextLlm(
                context.prompt(),
                context.systemPrompt(),
                context.vendor(),
                context.model()
        );
    }

    @Override
    public MultimodalTargetType getTargetType() {
        return MultimodalTargetType.ANALYZE_PDF;
    }

    private String validatePdfContentType(String contentType) {
        return MediaTypePolicy.validatePdfOrThrow(contentType);
    }
}
