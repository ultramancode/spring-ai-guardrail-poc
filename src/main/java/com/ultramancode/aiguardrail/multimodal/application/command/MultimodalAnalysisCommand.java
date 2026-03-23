package com.ultramancode.aiguardrail.multimodal.application.command;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import lombok.Builder;
import lombok.Getter;

/**
 * 멀티모달 분석(이미지/PDF) 공통 요청 명령 객체.
 */
@Getter
@Builder
public class MultimodalAnalysisCommand {

    private final AttachmentFile file;
    private final String question;
    private final String traceId;
    private final String systemPrompt;
    private final String vendor;
    private final String model;
}
