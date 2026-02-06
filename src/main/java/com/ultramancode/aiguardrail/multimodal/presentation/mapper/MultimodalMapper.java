package com.ultramancode.aiguardrail.multimodal.presentation.mapper;

import com.ultramancode.aiguardrail.multimodal.application.command.MultimodalAnalysisCommand;
import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;
import com.ultramancode.aiguardrail.multimodal.presentation.response.VisionResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 멀티모달 관련 매핑 유틸리티
 */
public class MultimodalMapper {

    public static MultimodalAnalysisCommand toCommand(MultipartFile file, String question, String traceId) {
        return MultimodalAnalysisCommand.builder()
                .file(file)
                .question(question)
                .traceId(traceId)
                .build();
    }

    public static VisionResponse toResponse(MultimodalAnalysisResult result) {
        return VisionResponse.builder()
                .answer(result.getAnswer())
                .traceId(result.getTraceId())
                .processingMode(result.getProcessingMode())
                .originalFileName(result.getOriginalFileName())
                .extractedText(result.getExtractedText())
                .build();
    }
}
