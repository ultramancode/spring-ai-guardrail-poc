package com.ultramancode.aiguardrail.guardrail.application.port.in;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import org.springframework.lang.Nullable;

/**
 * 외부 모듈에서 가드레일 채팅을 호출하기 위한 파사드 유스케이스입니다.
 */
public interface GuardrailChatFacadeUseCase {

    GuardrailChatFacadeResult chat(GuardrailChatFacadeCommand command);

    record GuardrailChatFacadeCommand(
            String text,
            @Nullable AttachmentFile file,
            @Nullable String vendor,
            @Nullable String model,
            @Nullable String targetGuardrail,
            @Nullable String systemPrompt
    ) {
    }

    record GuardrailChatFacadeResult(
            String output,
            @Nullable String observationId
    ) {
    }
}
