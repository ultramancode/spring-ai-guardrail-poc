package com.ultramancode.aiguardrail.guardrail.infrastructure.handler;

import com.ultramancode.aiguardrail.common.document.port.out.DocumentParserPort;
import com.ultramancode.aiguardrail.common.observability.domain.AnalysisMode;
import com.ultramancode.aiguardrail.common.util.MediaProcessingUtils;
import com.ultramancode.aiguardrail.common.util.MediaTypePolicy;
import com.ultramancode.aiguardrail.common.util.MultimodalValidationUtils;
import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;
import com.ultramancode.aiguardrail.guardrail.application.handler.PiiChatHandler;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.guardrail.application.result.PiiChatResult;
import com.ultramancode.aiguardrail.guardrail.infrastructure.support.ChatHandlerExceptionSupport;
import com.ultramancode.aiguardrail.guardrail.infrastructure.support.GuardrailChatExecutionSupport;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PdfChatHandler implements PiiChatHandler {

    private final DocumentParserPort documentParser;
    private final PiiUseCase piiService;
    private final GuardrailChatExecutionSupport chatExecutionSupport;

    @Value("${guardrail.pdf.on-extract-fail:llm_notice}")
    private String onPdfExtractFail;

    @Value("${guardrail.pdf.max-extracted-chars:20000}")
    private int maxPdfExtractChars;

    @Value("${guardrail.multimodal.max-file-size-bytes:10485760}")
    private long maxMultimodalFileSizeBytes;

    @Override
    public boolean supports(PiiChatCommand command) {
        if (command.getFile() == null || command.getFile().isEmpty()) {
            return false;
        }

        String contentType = command.getFile().getContentType();
        if (contentType == null) {
            return false;
        }

        return MediaTypePolicy.isPdf(contentType);
    }

    @Override
    public PiiChatResult handle(ChatClient client, PiiChatCommand command, PromptTemplate prompt) {
        try {
            String userInput = command.getText();

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
                    piiService::tokenize,
                    message -> log.warn("[PDF-CHAT] {}", message)
            );
            String maskedExtractedText = MultimodalValidationUtils.truncateText(
                    pdfProcessingResult.maskedExtractedText(),
                    maxPdfExtractChars,
                    "PDF 추출 텍스트",
                    message -> log.warn("[PDF-CHAT] {}", message)
            );

            String augmentedInput = userInput + "\n\n[PDF 내용]\n" + maskedExtractedText;

            return chatExecutionSupport.executeText(
                    client,
                    command,
                    prompt,
                    augmentedInput,
                    AnalysisMode.ANALYZE_PDF,
                    maskedExtractedText
            );
        } catch (RuntimeException e) {
            throw ChatHandlerExceptionSupport.rethrow(e, "Failed to process PDF chat");
        }
    }

    @Override
    public int priority() {
        return 20;
    }
}
