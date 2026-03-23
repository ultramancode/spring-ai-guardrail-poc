package com.ultramancode.aiguardrail.multimodal.application.service;

import com.ultramancode.aiguardrail.common.llm.application.port.out.LlmPort;
import com.ultramancode.aiguardrail.common.observability.domain.AnalysisMode;
import com.ultramancode.aiguardrail.common.observability.domain.GenerationAttachment;
import com.ultramancode.aiguardrail.common.observability.port.out.ObservabilityPort;
import com.ultramancode.aiguardrail.common.pii.port.out.PiiProcessingPort;
import com.ultramancode.aiguardrail.common.util.MediaProcessingUtils;
import com.ultramancode.aiguardrail.common.util.MultimodalValidationUtils;
import com.ultramancode.aiguardrail.multimodal.application.command.MultimodalAnalysisCommand;
import com.ultramancode.aiguardrail.multimodal.application.model.LlmExecutionData;
import com.ultramancode.aiguardrail.multimodal.application.port.in.AnalyzeImageUseCase;
import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;
import com.ultramancode.aiguardrail.multimodal.domain.MultimodalTargetType;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AnalyzeImageService extends AbstractMultimodalService implements AnalyzeImageUseCase {

    @Value("${guardrail.multimodal.max-file-size-bytes:10485760}")
    private long maxMultimodalFileSizeBytes;

    public AnalyzeImageService(
            LlmPort llmPort,
            ObservabilityPort observabilityPort,
            PiiProcessingPort piiProcessingPort,
            ObservationRegistry observationRegistry
    ) {
        super(llmPort, observabilityPort, piiProcessingPort, observationRegistry);
    }

    @Override
    public MultimodalAnalysisResult analyze(MultimodalAnalysisCommand command) {
        log.info("[VISION] Analyzing image: {}", command.getFile().getOriginalFilename());
        return super.runAnalysisPipeline(
                command,
                AnalysisMode.ANALYZE_IMAGE.getValue(),
                AnalysisMode.ANALYZE_IMAGE.getValue()
        );
    }

    @Override
    protected LlmExecutionData prepare(MultimodalAnalysisCommand command, String maskedQuestion) {
        MediaProcessingUtils.ImagePayload imagePayload = MediaProcessingUtils.prepareImagePayload(command.getFile());
        String contentType = imagePayload.contentType();
        byte[] imageBytes = imagePayload.bytes();
        MultimodalValidationUtils.validateFileSize(
                imageBytes.length,
                command.getFile().getOriginalFilename(),
                maxMultimodalFileSizeBytes,
                "image"
        );

        return LlmExecutionData.builder()
                .prompt(maskedQuestion)
                .systemPrompt(command.getSystemPrompt())
                .vendor(command.getVendor())
                .model(command.getModel())
                .attachment(new GenerationAttachment(
                        command.getFile().getOriginalFilename(),
                        contentType,
                        imageBytes,
                        null
                ))
                .mediaSource(new ByteArrayResource(imageBytes))
                .mediaType(contentType)
                .build();
    }

    @Override
    protected ChatResponse executeLlm(LlmExecutionData context) {
        return executeDefaultMediaLlm(
                context.prompt(),
                context.mediaSource(),
                context.mediaType(),
                context.systemPrompt(),
                context.vendor(),
                context.model()
        );
    }

    @Override
    public MultimodalTargetType getTargetType() {
        return MultimodalTargetType.ANALYZE_IMAGE;
    }
}
