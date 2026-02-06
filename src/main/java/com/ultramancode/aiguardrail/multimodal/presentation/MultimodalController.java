package com.ultramancode.aiguardrail.multimodal.presentation;

import com.ultramancode.aiguardrail.multimodal.application.port.in.AnalyzeImageUseCase;
import com.ultramancode.aiguardrail.multimodal.application.port.in.AnalyzePdfUseCase;
import com.ultramancode.aiguardrail.multimodal.presentation.mapper.MultimodalMapper;
import com.ultramancode.aiguardrail.multimodal.presentation.response.VisionResponse;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 멀티모달(Multimodal) 테스트 컨트롤러 (Refactored)
 */
@Slf4j
@RestController
@RequestMapping("/api/multimodal")
@RequiredArgsConstructor
public class MultimodalController {

    private final AnalyzeImageUseCase analyzeImageUseCase;
    private final AnalyzePdfUseCase analyzePdfUseCase;

    /**
     * 이미지 분석 (Vision Direct)
     */
    @PostMapping(value = "/analyze-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VisionResponse> analyzeImage(
            @RequestPart("file") MultipartFile imageFile,
            @RequestParam(value = "question", defaultValue = "이 이미지에 무엇이 있나요?") String question) {

        String traceId = getCurrentTraceId();

        var result = analyzeImageUseCase.execute(MultimodalMapper.toCommand(imageFile, question, traceId));

        return ResponseEntity.ok(MultimodalMapper.toResponse(result));
    }

    /**
     * PDF 분석 (Parsed Content)
     */
    @PostMapping(value = "/analyze-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VisionResponse> analyzePdf(
            @RequestPart("file") MultipartFile pdfFile,
            @RequestParam(value = "question", defaultValue = "이 문서의 핵심 내용을 요약해주세요.") String question) {

        String traceId = getCurrentTraceId();

        var result = analyzePdfUseCase.execute(MultimodalMapper.toCommand(pdfFile, question, traceId));

        return ResponseEntity.ok(MultimodalMapper.toResponse(result));
    }

    private String getCurrentTraceId() {
        return Span.current().getSpanContext().getTraceId();
    }
}
