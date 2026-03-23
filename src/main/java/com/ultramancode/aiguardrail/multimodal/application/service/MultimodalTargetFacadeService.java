package com.ultramancode.aiguardrail.multimodal.application.service;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import com.ultramancode.aiguardrail.multimodal.application.command.MultimodalAnalysisCommand;
import com.ultramancode.aiguardrail.multimodal.application.port.in.MultimodalAnalysisUseCase;
import com.ultramancode.aiguardrail.multimodal.application.port.in.MultimodalTargetFacadeUseCase;
import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;
import com.ultramancode.aiguardrail.multimodal.domain.MultimodalTargetType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 멀티모달 분석 파사드 구현체입니다.
 */
@Service
public class MultimodalTargetFacadeService implements MultimodalTargetFacadeUseCase {

    private final Map<MultimodalTargetType, MultimodalAnalysisUseCase> useCaseMap =
            new EnumMap<>(MultimodalTargetType.class);

    public MultimodalTargetFacadeService(List<MultimodalAnalysisUseCase> useCases) {
        for (MultimodalAnalysisUseCase useCase : useCases) {
            MultimodalTargetType targetType = useCase.getTargetType();
            MultimodalAnalysisUseCase previousUseCase = useCaseMap.put(targetType, useCase);
            if (previousUseCase != null) {
                throw new IllegalStateException("Duplicate multimodal use case registration: " + targetType);
            }
        }
    }

    @Override
    public MultimodalTargetFacadeResult analyze(MultimodalTargetFacadeCommand command) {
        validateCommandNotNull(command);

        MultimodalTargetType targetType = resolveTargetType(command.targetType());
        validateRequiredFileForTarget(command, targetType);

        MultimodalAnalysisUseCase useCase = resolveUseCase(targetType);
        MultimodalAnalysisCommand multimodalCommand = MultimodalAnalysisCommand.builder()
                .question(command.question())
                .file(command.file())
                .traceId(command.traceId())
                .systemPrompt(command.systemPrompt())
                .vendor(command.vendor())
                .model(command.model())
                .build();

        MultimodalAnalysisResult result = useCase.analyze(multimodalCommand);
        return new MultimodalTargetFacadeResult(
                result.getAnswer(),
                result.getMaskedAnswer(),
                result.getObservationId()
        );
    }

    private void validateCommandNotNull(MultimodalTargetFacadeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Multimodal command must not be null.");
        }
    }

    private MultimodalTargetType resolveTargetType(String targetTypeValue) {
        if (targetTypeValue == null || targetTypeValue.isBlank()) {
            throw new IllegalArgumentException("targetType must not be blank.");
        }

        try {
            return MultimodalTargetType.valueOf(targetTypeValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported multimodal target type: " + targetTypeValue, e);
        }
    }

    private void validateRequiredFileForTarget(
            MultimodalTargetFacadeCommand command,
            MultimodalTargetType targetType
    ) {
        validateRequiredFile(targetType, command.file());
    }

    private void validateRequiredFile(MultimodalTargetType targetType, AttachmentFile file) {
        if (targetType == MultimodalTargetType.ANALYZE_IMAGE || targetType == MultimodalTargetType.ANALYZE_PDF) {
            if (file == null) {
                throw new IllegalArgumentException("File is required for target type: " + targetType);
            }
            if (file.isEmpty()) {
                throw new IllegalArgumentException("File must not be empty for target type: " + targetType);
            }
        }
    }

    private MultimodalAnalysisUseCase resolveUseCase(MultimodalTargetType targetType) {
        MultimodalAnalysisUseCase useCase = useCaseMap.get(targetType);
        if (useCase == null) {
            throw new IllegalArgumentException("No multimodal use case registered for target type: " + targetType);
        }

        return useCase;
    }
}
