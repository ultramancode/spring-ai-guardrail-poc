package com.ultramancode.aiguardrail.experiment.application.service;

import com.ultramancode.aiguardrail.multimodal.application.command.MultimodalAnalysisCommand;
import com.ultramancode.aiguardrail.experiment.domain.ExperimentTarget;
import com.ultramancode.aiguardrail.multimodal.application.port.in.AnalyzePdfUseCase;
import com.ultramancode.aiguardrail.multimodal.application.port.in.AnalyzeImageUseCase;
import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class ExperimentTargetResolver {

    private final AnalyzePdfUseCase analyzePdfUseCase;
    private final AnalyzeImageUseCase analyzeImageUseCase;

    private final Map<ExperimentTarget, Object> targetMap = new HashMap<>();

    @PostConstruct
    public void init() {
        targetMap.put(ExperimentTarget.ANALYZE_PDF, analyzePdfUseCase);
        targetMap.put(ExperimentTarget.ANALYZE_IMAGE, analyzeImageUseCase);
    }

    public Object resolve(ExperimentTarget target) {
        Object useCase = targetMap.get(target);
        if (useCase == null) {
            throw new IllegalArgumentException("Unsupported experiment target: " + target);
        }
        return useCase;
    }

    public Object resolveAndExecute(ExperimentTarget target, String question, MultipartFile file, String traceId) {
        Object useCase = resolve(target);
        MultimodalAnalysisCommand command = MultimodalAnalysisCommand.builder()
                .question(question)
                .file(file)
                .traceId(traceId)
                .experiment(true)
                .build();

        if (useCase instanceof AnalyzePdfUseCase) {
            return ((AnalyzePdfUseCase) useCase).execute(command);
        } else if (useCase instanceof AnalyzeImageUseCase) {
            return ((AnalyzeImageUseCase) useCase).execute(command);
        }
        throw new IllegalArgumentException("Unsupported use case type for execution: " + useCase.getClass());
    }
}
