package com.ultramancode.aiguardrail.guardrail.application.handler;

import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;
import com.ultramancode.aiguardrail.guardrail.application.result.PiiChatResult;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptTemplate;
import org.springframework.ai.chat.client.ChatClient;

/**
 * PII 채팅 처리 핸들러 인터페이스.
 */
public interface PiiChatHandler {

    /**
     * 현재 핸들러가 요청을 처리할 수 있는지 확인한다.
     */
    boolean supports(PiiChatCommand command);

    /**
     * 실제 채팅 로직을 수행한다.
     */
    PiiChatResult handle(ChatClient client, PiiChatCommand command, PromptTemplate prompt);

    /**
     * 핸들러 우선순위를 반환한다. 값이 작을수록 우선 처리된다.
     */
    default int priority() {
        return 100;
    }
}

