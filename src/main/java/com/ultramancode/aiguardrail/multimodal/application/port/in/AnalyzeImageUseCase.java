package com.ultramancode.aiguardrail.multimodal.application.port.in;

import com.ultramancode.aiguardrail.multimodal.application.command.MultimodalAnalysisCommand;
import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;

public interface AnalyzeImageUseCase {
    MultimodalAnalysisResult execute(MultimodalAnalysisCommand command);
}
