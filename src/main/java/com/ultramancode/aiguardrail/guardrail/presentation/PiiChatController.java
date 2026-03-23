package com.ultramancode.aiguardrail.guardrail.presentation;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import com.ultramancode.aiguardrail.common.observability.port.out.ObservabilityPort;
import com.ultramancode.aiguardrail.common.util.MediaTypePolicy;
import com.ultramancode.aiguardrail.common.util.MultimodalValidationUtils;
import com.ultramancode.aiguardrail.common.util.TraceContentPolicy;
import com.ultramancode.aiguardrail.common.web.mapper.MultipartAttachmentMapper;
import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiChatUseCase;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.guardrail.application.result.PiiChatResult;
import com.ultramancode.aiguardrail.guardrail.presentation.request.PiiChatRequest;
import com.ultramancode.aiguardrail.guardrail.presentation.response.GuardrailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/pii")
@RequiredArgsConstructor
public class PiiChatController {

    private final PiiChatUseCase piiChatUseCase;
    private final PiiUseCase piiUseCase;
    private final ObservabilityPort observabilityPort;

    @Value("${guardrail.pii.trace-raw-content:false}")
    private boolean traceRawContent;

    @Value("${guardrail.multimodal.max-file-size-bytes:10485760}")
    private long maxMultimodalFileSizeBytes;

    @Value("${guardrail.multimodal.max-text-length:4000}")
    private int maxMultimodalTextLength;

    @PostMapping({"/test", "/chat"})
    public ResponseEntity<GuardrailResponse> chat(@RequestBody @Valid PiiChatRequest request) {
        return handleTextOnlyChat(request, "[API-IN]", "[API-OUT]");
    }

    @PostMapping({"/test-mcp", "/chat-mcp"})
    public ResponseEntity<GuardrailResponse> chatWithMcp(@RequestBody @Valid PiiChatRequest request) {
        return handleTextOnlyChat(request, "[API-IN-MCP]", "[API-OUT-MCP]");
    }

    @PostMapping(value = {"/test-mcp-multimodal", "/chat-multimodal"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GuardrailResponse> chatMultimodal(
            @RequestPart("text") String text,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "vendor", required = false) String vendor,
            @RequestPart(value = "model", required = false) String model
    ) {
        return handleMultimodalChat(text, file, vendor, model);
    }

    private ResponseEntity<GuardrailResponse> handleTextOnlyChat(
            PiiChatRequest request,
            String inputLogPrefix,
            String outputLogPrefix
    ) {
        String userInput = request.getText();
        traceRequest(inputLogPrefix, userInput, request.getVendor(), request.getModel());

        PiiChatResult chatResult = piiChatUseCase.chat(PiiChatCommand.builder()
                .text(userInput)
                .vendor(request.getVendor())
                .model(request.getModel())
                .build());

        String resolvedResponse = resolveResponse(chatResult);
        traceResponse(outputLogPrefix, resolvedResponse);

        return ResponseEntity.ok(GuardrailResponse.success(userInput, resolvedResponse));
    }

    private ResponseEntity<GuardrailResponse> handleMultimodalChat(
            String text,
            MultipartFile file,
            String vendor,
            String model
    ) {
        String normalizedText = validateMultimodalInput(text, file);

        traceRequest("[API-IN-MCP-MULTI]", normalizedText, vendor, model);

        AttachmentFile attachmentFile = MultipartAttachmentMapper.fromMultipartFile(file);
        PiiChatResult chatResult = piiChatUseCase.chat(PiiChatCommand.builder()
                .text(normalizedText)
                .file(attachmentFile)
                .vendor(vendor)
                .model(model)
                .build());

        String resolvedResponse = resolveResponse(chatResult);
        traceResponse("[API-OUT-MCP-MULTI]", resolvedResponse);

        return ResponseEntity.ok(GuardrailResponse.success(normalizedText, resolvedResponse));
    }

    private void traceRequest(String inputLogPrefix, String rawInput, String vendor, String model) {
        String traceInput = TraceContentPolicy.resolve(rawInput, traceRawContent, piiUseCase::tokenizeWithoutObservation);
        log.info("{} Request: {}, Vendor: {}, Model: {}", inputLogPrefix, traceInput, vendor, model);
        observabilityPort.traceInput(traceInput);
    }

    private void traceResponse(String outputLogPrefix, String response) {
        String traceOutput = TraceContentPolicy.resolve(response, traceRawContent, piiUseCase::tokenizeWithoutObservation);
        log.info("{} Response: \"{}\"", outputLogPrefix, traceOutput);
        observabilityPort.traceOutput(traceOutput);
    }

    private String resolveResponse(PiiChatResult chatResult) {
        if (chatResult == null) {
            throw new IllegalStateException("Pii chat returned null result.");
        }

        String response = chatResult.output();
        if (response == null) {
            throw new IllegalStateException("Pii chat returned null output.");
        }
        return response;
    }

    private String validateMultimodalInput(String text, MultipartFile file) {
        String normalizedText = MultimodalValidationUtils.normalizeAndValidateText(
                text,
                "text",
                maxMultimodalTextLength
        );
        MultimodalValidationUtils.validateMultipartFile(file, maxMultimodalFileSizeBytes, "multimodal");

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("File contentType is required.");
        }
        if (!MediaTypePolicy.isSupportedMultimodal(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported file contentType: " + contentType
                            + ". Supported types: application/pdf, image/*"
            );
        }

        return normalizedText;
    }
}
