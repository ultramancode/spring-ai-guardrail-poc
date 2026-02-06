package com.ultramancode.aiguardrail.guardrail.presentation;

import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;
import com.ultramancode.aiguardrail.guardrail.domain.PiiContextHolder;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiChatUseCase;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.guardrail.application.port.out.GuardrailObservabilityPort;
import com.ultramancode.aiguardrail.guardrail.presentation.request.PiiChatRequest;
import com.ultramancode.aiguardrail.guardrail.presentation.response.GuardrailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

/**
 * PII 보호 데모 컨트롤러 (Refactored)
 */
@Slf4j
@RestController
@RequestMapping("/api/pii")
@RequiredArgsConstructor
public class PiiChatController {

    private final PiiChatUseCase piiChatUseCase;
    private final PiiUseCase piiService;
    private final GuardrailObservabilityPort guardrailPort;

    @PostMapping("/test")
    public GuardrailResponse testPii(@RequestBody @Valid PiiChatRequest request) {
        String userInput = request.getText();
        try {
            log.info("[API-IN] User Request: \"{}\"", userInput);

            // [Observability] Root Span Input Tracing (Tokenized)
            // Use INTERNAL version to avoid creating redundant child spans in Langfuse UI
            String maskedInput = piiService.tokenizeInternal(userInput);
            guardrailPort.traceInput(maskedInput);

            String response = piiChatUseCase.chat(PiiChatCommand.builder()
                    .text(userInput)
                    .useMcp(false)
                    .build());

            log.info("[API-OUT] Final Response: \"{}\"", response);

            // [Observability] Root Span Output Tracing (Tokenized)
            String maskedOutput = response != null ? piiService.tokenizeInternal(response) : "";
            guardrailPort.traceOutput(maskedOutput);

            return GuardrailResponse.success(userInput, response != null ? response : "(No Content)");

        } catch (SecurityException e) {
            log.warn("[GUARDRAIL] Request blocked: {}", e.getMessage());
            return GuardrailResponse.blocked(userInput, "PROMPT_INJECTION_DETECTED");
        } finally {
            PiiContextHolder.clearContext();
        }
    }

    @PostMapping("/test-mcp")
    public GuardrailResponse testPiiRealMcp(@RequestBody @Valid PiiChatRequest request) {
        String userInput = request.getText();
        try {
            log.info("[API-IN-MCP] User Request: \"{}\"", userInput);

            // [Observability] Root Span Input Tracing (Tokenized)
            String maskedInput = piiService.tokenizeInternal(userInput);
            guardrailPort.traceInput(maskedInput);

            String response = piiChatUseCase.chat(PiiChatCommand.builder()
                    .text(userInput)
                    .useMcp(true)
                    .build());

            log.info("[API-OUT-MCP] Final Response: \"{}\"", response);

            // [Observability] Root Span Output Tracing (Tokenized)
            String maskedOutput = response != null ? piiService.tokenizeInternal(response) : "";
            guardrailPort.traceOutput(maskedOutput);

            return GuardrailResponse.success(userInput, response != null ? response : "(No Content)");

        } catch (SecurityException e) {
            log.warn("[GUARDRAIL] Request blocked: {}", e.getMessage());
            return GuardrailResponse.blocked(userInput, "PROMPT_INJECTION_DETECTED");
        } finally {
            PiiContextHolder.clearContext();
        }
    }

    @PostMapping(value = "/test-mcp-multimodal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GuardrailResponse testPiiMcpMultimodal(
            @RequestPart("text") String text,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        try {
            log.info("[API-IN-MCP-MULTI] User Request: \"{}\"", text);

            // [Observability] Root Span Input Tracing (Tokenized)
            String maskedInput = piiService.tokenizeInternal(text);
            guardrailPort.traceInput(maskedInput);

            String response = piiChatUseCase.chat(PiiChatCommand.builder()
                    .text(text)
                    .file(file) // Pass file to Command
                    .useMcp(true)
                    .build());

            log.info("[API-OUT-MCP-MULTI] Final Response: \"{}\"", response);

            // [Observability] Root Span Output Tracing (Tokenized)
            String maskedOutput = response != null ? piiService.tokenizeInternal(response) : "";
            guardrailPort.traceOutput(maskedOutput);

            return GuardrailResponse.success(text, response != null ? response : "(No Content)");

        } catch (SecurityException e) {
            log.warn("[GUARDRAIL] Request blocked: {}", e.getMessage());
            return GuardrailResponse.blocked(text, "PROMPT_INJECTION_DETECTED");
        } finally {
            PiiContextHolder.clearContext();
        }
    }
}
