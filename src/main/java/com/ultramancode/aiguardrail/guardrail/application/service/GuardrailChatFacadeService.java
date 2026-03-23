package com.ultramancode.aiguardrail.guardrail.application.service;

import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;
import com.ultramancode.aiguardrail.guardrail.application.port.in.GuardrailChatFacadeUseCase;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiChatUseCase;
import com.ultramancode.aiguardrail.guardrail.application.result.PiiChatResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 가드레일 채팅 파사드 구현체입니다.
 */
@Service
@RequiredArgsConstructor
public class GuardrailChatFacadeService implements GuardrailChatFacadeUseCase {

    private final PiiChatUseCase piiChatUseCase;

    @Override
    public GuardrailChatFacadeResult chat(GuardrailChatFacadeCommand command) {
        PiiChatResult result = piiChatUseCase.chat(PiiChatCommand.builder()
                .text(command.text())
                .file(command.file())
                .vendor(command.vendor())
                .model(command.model())
                .targetGuardrail(command.targetGuardrail())
                .systemPrompt(command.systemPrompt())
                .build());

        return new GuardrailChatFacadeResult(result.output(), result.observationId());
    }
}
