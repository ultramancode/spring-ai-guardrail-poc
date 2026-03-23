package com.ultramancode.aiguardrail.guardrail.infrastructure.handler;

import com.ultramancode.aiguardrail.common.observability.domain.AnalysisMode;
import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;
import com.ultramancode.aiguardrail.guardrail.application.handler.PiiChatHandler;
import com.ultramancode.aiguardrail.guardrail.application.result.PiiChatResult;
import com.ultramancode.aiguardrail.guardrail.infrastructure.support.GuardrailChatExecutionSupport;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TextChatHandler implements PiiChatHandler {

    private final GuardrailChatExecutionSupport chatExecutionSupport;

    @Override
    public boolean supports(PiiChatCommand command) {
        if (command.getFile() == null) {
            return true;
        }
        if (command.getFile().isEmpty()) {
            return true;
        }
        return false;
    }

    @Override
    public PiiChatResult handle(ChatClient client, PiiChatCommand command, PromptTemplate prompt) {
        return chatExecutionSupport.executeText(
                client,
                command,
                prompt,
                command.getText(),
                AnalysisMode.CHAT_TEXT,
                null
        );
    }

    @Override
    public int priority() {
        return 30;
    }
}

