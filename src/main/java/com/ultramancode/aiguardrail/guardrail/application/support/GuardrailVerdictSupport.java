package com.ultramancode.aiguardrail.guardrail.application.support;

import com.ultramancode.aiguardrail.common.llm.application.port.out.LlmPort;
import com.ultramancode.aiguardrail.guardrail.domain.SafetyVerdict;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 가드레일 분류용 LLM 호출과 SafetyVerdict 검증을 공통으로 수행합니다.
 */
@Component
@RequiredArgsConstructor
public class GuardrailVerdictSupport {

    private final LlmPort llmPort;

    public SafetyVerdict classify(
            String vendor,
            String model,
            String systemPrompt,
            String userInput,
            String detectorName
    ) {
        SafetyVerdict verdict = llmPort.getChatClient(vendor, model)
                .prompt()
                .system(systemPrompt)
                .user(userInput)
                .call()
                .entity(SafetyVerdict.class);

        SafetyVerdictSupport.validateOrThrow(verdict, detectorName);
        return verdict;
    }
}
