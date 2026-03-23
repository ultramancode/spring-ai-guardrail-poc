package com.ultramancode.aiguardrail.multimodal.presentation.mapper;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import com.ultramancode.aiguardrail.common.web.mapper.MultipartAttachmentMapper;
import com.ultramancode.aiguardrail.multimodal.application.command.MultimodalAnalysisCommand;
import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;
import com.ultramancode.aiguardrail.multimodal.presentation.response.MultimodalResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 멀티모달 계층 간 매핑 유틸리티.
 */
public final class MultimodalMapper {

    private MultimodalMapper() {
    }

    public static MultimodalAnalysisCommand toCommand(
            MultipartFile file,
            String question,
            String traceId,
            String systemPrompt,
            String vendor,
            String model
    ) {
        AttachmentFile attachmentFile = MultipartAttachmentMapper.fromMultipartFile(file);
        return MultimodalAnalysisCommand.builder()
                .file(attachmentFile)
                .question(question)
                .traceId(traceId)
                .systemPrompt(systemPrompt)
                .vendor(vendor)
                .model(model)
                .build();
    }

    public static MultimodalResponse toResponse(MultimodalAnalysisResult result) {
        return MultimodalResponse.builder()
                .answer(result.getAnswer())
                .traceId(result.getTraceId())
                .processingMode(result.getProcessingMode())
                .originalFileName(result.getOriginalFileName())
                .build();
    }
}
