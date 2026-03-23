package com.ultramancode.aiguardrail.multimodal.application.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultimodalAnalysisResult {

    private String answer;
    private String traceId;
    private String observationId;
    private String maskedAnswer;
    private String processingMode;
    private String originalFileName;
}
