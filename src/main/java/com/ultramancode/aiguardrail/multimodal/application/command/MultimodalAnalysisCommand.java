package com.ultramancode.aiguardrail.multimodal.application.command;

import lombok.Builder;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 멀티모달 분석(이미지/PDF 등)을 위한 공통 명령 객체
 */
@Getter
@Builder
public class MultimodalAnalysisCommand {
    private final MultipartFile file;
    private final String question;
    private final String traceId;
    private final boolean experiment;
}
