package com.ultramancode.aiguardrail.guardrail.application.service;

import com.ultramancode.aiguardrail.common.llm.application.port.out.LlmPort;
import com.ultramancode.aiguardrail.common.observability.LangfuseConstants;
import com.ultramancode.aiguardrail.common.observability.domain.ObservationType;
import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;
import com.ultramancode.aiguardrail.guardrail.application.handler.PiiChatHandler;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiChatUseCase;
import com.ultramancode.aiguardrail.guardrail.application.result.PiiChatResult;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptPort;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptTemplate;
import com.ultramancode.aiguardrail.prompt.domain.PromptConstants;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * PII 보호 채팅 유스케이스 구현체.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PiiChatService implements PiiChatUseCase {

    private final LlmPort llmPort;
    private final ObservationRegistry observationRegistry;
    private final List<PiiChatHandler> handlers;
    private final PromptPort promptPort;

    @Value("${guardrail.pii.prompt-name:" + PromptConstants.PROMPT_PII_SYSTEM + "}")
    private String piiPromptName;

    @Override
    public PiiChatResult chat(PiiChatCommand command) {
        return Observation.createNotStarted("pii.check", observationRegistry)
                .lowCardinalityKeyValue(LangfuseConstants.TAG_OBSERVATION_TYPE, ObservationType.GUARDRAIL.getValue())
                .observe(() -> {
                    PromptTemplate prompt = fetchPrompt(command);
                    ChatClient chatClient = createChatClient(command);

                    List<PiiChatHandler> supportedHandlers = handlers.stream()
                            .sorted(
                                    Comparator.comparingInt(PiiChatHandler::priority)
                                            .thenComparing(handler -> handler.getClass().getName())
                            )
                            .filter(handler -> handler.supports(command))
                            .toList();

                    if (supportedHandlers.isEmpty()) {
                        throw new IllegalArgumentException("No handler can process this request.");
                    }
                    if (supportedHandlers.size() > 1) {
                        List<String> candidates = supportedHandlers.stream()
                                .map(handler -> handler.getClass().getSimpleName())
                                .toList();
                        throw new IllegalStateException(
                                "Handler matching is ambiguous. Adjust priority or support conditions. candidates=" + candidates
                        );
                    }

                    PiiChatHandler handler = supportedHandlers.get(0);
                    log.debug("[PII CHAT] Using handler: {}", handler.getClass().getSimpleName());
                    return handler.handle(chatClient, command, prompt);
                });
    }

    private PromptTemplate fetchPrompt(PiiChatCommand command) {
        if (command.getSystemPrompt() != null && !command.getSystemPrompt().isBlank()) {
            return new PromptTemplate(command.getSystemPrompt(), "inline-system-prompt", 0);
        }

        try {
            return promptPort.fetchPromptOrThrow(piiPromptName);
        } catch (RuntimeException e) {
            log.warn(
                    "[PII CHAT] Failed to load prompt '{}'. Falling back to local prompt. cause={}",
                    piiPromptName,
                    e.getMessage(),
                    e
            );
            return new PromptTemplate("""
                    당신은 개인정보를 안전하게 처리하는 고객지원 AI 어시스턴트입니다.
                    
                    [작업 지침]
                    1. 사용자 입력에는 토큰화된 개인정보가 포함될 수 있습니다. (예: [PERSON_1], [PHONE_NUMBER_1])
                    2. 사용자 요청 의도를 파악하고 필요한 정보만 정확하게 사용하세요.
                    3. 개인정보 조회/검증이 필요하면 제공된 도구를 사용하세요.
                    4. 도구 실행 결과를 바탕으로 간결하고 친절하게 답변하세요.
                    5. 불확실한 추측이나 과도한 설명은 피하세요.
                    
                    [예시]
                    User: "전화번호 [PHONE_NUMBER_1]로 주소 조회해줘."
                    Assistant: (phone='[PHONE_NUMBER_1]'로 searchAddress 도구 실행)
                    
                    User: "이름 [PERSON_1], 전화번호 [PHONE_NUMBER_1]로 본인 확인해줘."
                    Assistant: (name='[PERSON_1]', phone='[PHONE_NUMBER_1]'로 verifyUser 도구 실행)
                    """, "fallback", 0);
        }
    }

    private ChatClient createChatClient(PiiChatCommand command) {
        return llmPort.getChatClient(command.getVendor(), command.getModel());
    }
}

