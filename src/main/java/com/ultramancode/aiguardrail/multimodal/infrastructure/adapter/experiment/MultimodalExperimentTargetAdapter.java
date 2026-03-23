package com.ultramancode.aiguardrail.multimodal.infrastructure.adapter.experiment;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentTargetPort;
import com.ultramancode.aiguardrail.multimodal.application.port.in.MultimodalTargetFacadeUseCase;
import org.springframework.stereotype.Component;

/**
 * ExperimentTargetPort를 멀티모달 유스케이스 구현체에 연결하는 어댑터입니다.
 */
@Component
public class MultimodalExperimentTargetAdapter implements ExperimentTargetPort {

    private final MultimodalTargetFacadeUseCase multimodalTargetFacadeUseCase;

    public MultimodalExperimentTargetAdapter(MultimodalTargetFacadeUseCase multimodalTargetFacadeUseCase) {
        this.multimodalTargetFacadeUseCase = multimodalTargetFacadeUseCase;
    }

    @Override
    public ExperimentTargetResult execute(
            String targetType,
            String question,
            AttachmentFile file,
            String traceId,
            String systemPrompt,
            String vendor,
            String model
    ) {
        MultimodalTargetFacadeUseCase.MultimodalTargetFacadeResult result = multimodalTargetFacadeUseCase.analyze(
                new MultimodalTargetFacadeUseCase.MultimodalTargetFacadeCommand(
                        targetType,
                        question,
                        file,
                        traceId,
                        systemPrompt,
                        vendor,
                        model
                )
        );

        return new ExperimentTargetResult(
                result.answer(),
                result.maskedAnswer(),
                result.observationId()
        );
    }
}

