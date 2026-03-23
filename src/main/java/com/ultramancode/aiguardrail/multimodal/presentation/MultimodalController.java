package com.ultramancode.aiguardrail.multimodal.presentation;

import com.ultramancode.aiguardrail.common.observability.TraceIdResolver;
import com.ultramancode.aiguardrail.common.util.MediaTypePolicy;
import com.ultramancode.aiguardrail.common.util.MultimodalValidationUtils;
import com.ultramancode.aiguardrail.multimodal.application.port.in.AnalyzeImageUseCase;
import com.ultramancode.aiguardrail.multimodal.application.port.in.AnalyzePdfUseCase;
import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;
import com.ultramancode.aiguardrail.multimodal.presentation.mapper.MultimodalMapper;
import com.ultramancode.aiguardrail.multimodal.presentation.response.MultimodalResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 멀티모달 분석 API 컨트롤러.
 */
@Slf4j
@RestController
@RequestMapping("/api/multimodal")
@RequiredArgsConstructor
public class MultimodalController {

    private final AnalyzeImageUseCase analyzeImageUseCase;
    private final AnalyzePdfUseCase analyzePdfUseCase;

    @Value("${guardrail.multimodal.max-file-size-bytes:10485760}")
    private long maxMultimodalFileSizeBytes;

    @Value("${guardrail.multimodal.max-text-length:4000}")
    private int maxMultimodalTextLength;

    /**
     * 이미지 분석 API.
     */
    @PostMapping(value = "/analyze-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MultimodalResponse> analyzeImage(
            @RequestPart("file") MultipartFile imageFile,
            @RequestParam(value = "question", defaultValue = "이 이미지에 무엇이 있나요?") String question,
            @RequestParam(value = "systemPrompt", required = false) String systemPrompt,
            @RequestParam(value = "vendor", required = false) String vendor,
            @RequestParam(value = "model", required = false) String model
    ) {
        String normalizedQuestion = validateMultimodalInput(question, imageFile);
        validateImageRequest(imageFile);

        String traceId = getCurrentTraceId();
        MultimodalAnalysisResult result = analyzeImageUseCase.analyze(
                MultimodalMapper.toCommand(imageFile, normalizedQuestion, traceId, systemPrompt, vendor, model)
        );
        return ResponseEntity.ok(MultimodalMapper.toResponse(result));
    }

    /**
     * PDF 분석 API.
     */
    @PostMapping(value = "/analyze-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MultimodalResponse> analyzePdf(
            @RequestPart("file") MultipartFile pdfFile,
            @RequestParam(value = "question", defaultValue = "이 문서의 핵심 내용을 요약해 주세요.") String question,
            @RequestParam(value = "systemPrompt", required = false) String systemPrompt,
            @RequestParam(value = "vendor", required = false) String vendor,
            @RequestParam(value = "model", required = false) String model
    ) {
        String normalizedQuestion = validateMultimodalInput(question, pdfFile);
        validatePdfRequest(pdfFile);

        String traceId = getCurrentTraceId();
        MultimodalAnalysisResult result = analyzePdfUseCase.analyze(
                MultimodalMapper.toCommand(pdfFile, normalizedQuestion, traceId, systemPrompt, vendor, model)
        );
        return ResponseEntity.ok(MultimodalMapper.toResponse(result));
    }

    private String getCurrentTraceId() {
        return TraceIdResolver.currentTraceIdOrNull();
    }

    private String validateMultimodalInput(String question, MultipartFile file) {
        String normalizedQuestion = MultimodalValidationUtils.normalizeAndValidateText(
                question,
                "question",
                maxMultimodalTextLength
        );
        MultimodalValidationUtils.validateMultipartFile(file, maxMultimodalFileSizeBytes, "multimodal");
        return normalizedQuestion;
    }

    private void validateImageRequest(MultipartFile file) {
        String contentType = file.getContentType();
        if (!MediaTypePolicy.isImage(contentType)) {
            throw new IllegalArgumentException("Image endpoint only accepts image/* content types.");
        }
    }

    private void validatePdfRequest(MultipartFile file) {
        String contentType = file.getContentType();
        if (!MediaTypePolicy.isPdf(contentType)) {
            throw new IllegalArgumentException("PDF endpoint only accepts application/pdf content type.");
        }
    }
}
