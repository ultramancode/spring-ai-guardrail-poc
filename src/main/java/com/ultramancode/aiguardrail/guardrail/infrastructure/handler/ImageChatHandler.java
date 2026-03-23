package com.ultramancode.aiguardrail.guardrail.infrastructure.handler;

import com.ultramancode.aiguardrail.common.observability.domain.AnalysisMode;
import com.ultramancode.aiguardrail.common.util.MediaProcessingUtils;
import com.ultramancode.aiguardrail.common.util.MediaTypePolicy;
import com.ultramancode.aiguardrail.common.util.MultimodalValidationUtils;
import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;
import com.ultramancode.aiguardrail.guardrail.application.handler.PiiChatHandler;
import com.ultramancode.aiguardrail.guardrail.application.result.PiiChatResult;
import com.ultramancode.aiguardrail.guardrail.infrastructure.support.ChatHandlerExceptionSupport;
import com.ultramancode.aiguardrail.guardrail.infrastructure.support.GuardrailChatExecutionSupport;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

@Component
@RequiredArgsConstructor
public class ImageChatHandler implements PiiChatHandler {

    private final GuardrailChatExecutionSupport chatExecutionSupport;

    @Value("${guardrail.multimodal.max-file-size-bytes:10485760}")
    private long maxMultimodalFileSizeBytes;

    @Override
    public boolean supports(PiiChatCommand command) {
        if (command.getFile() == null || command.getFile().isEmpty()) {
            return false;
        }

        String contentType = command.getFile().getContentType();
        if (contentType == null) {
            return false;
        }

        return MediaTypePolicy.isImage(contentType);
    }

    @Override
    public PiiChatResult handle(ChatClient client, PiiChatCommand command, PromptTemplate prompt) {
        try {
            String userInput = command.getText();
            MediaProcessingUtils.ImagePayload imagePayload = MediaProcessingUtils.prepareImagePayload(command.getFile());
            byte[] fileBytes = imagePayload.bytes();
            MultimodalValidationUtils.validateFileSize(
                    fileBytes.length,
                    command.getFile().getOriginalFilename(),
                    maxMultimodalFileSizeBytes,
                    "image"
            );
            MimeType mimeType = MimeTypeUtils.parseMimeType(imagePayload.contentType());
            Resource mediaResource = new ByteArrayResource(fileBytes);

            return chatExecutionSupport.executeImage(
                    client,
                    command,
                    prompt,
                    userInput,
                    mimeType,
                    mediaResource,
                    AnalysisMode.ANALYZE_IMAGE,
                    null
            );
        } catch (RuntimeException e) {
            throw ChatHandlerExceptionSupport.rethrow(e, "Failed to process image chat");
        }
    }

    @Override
    public int priority() {
        return 10;
    }
}
