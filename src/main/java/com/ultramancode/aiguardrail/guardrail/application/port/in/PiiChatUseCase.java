package com.ultramancode.aiguardrail.guardrail.application.port.in;

import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;

public interface PiiChatUseCase {
    String chat(PiiChatCommand command);
}
