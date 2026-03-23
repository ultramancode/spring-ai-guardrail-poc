package com.ultramancode.aiguardrail.guardrail.infrastructure.advisor;

import com.ultramancode.aiguardrail.common.llm.LlmConstants;
import com.ultramancode.aiguardrail.common.util.ChatResponseTextExtractor;
import com.ultramancode.aiguardrail.common.util.PromptTemplateSanitizer;
import com.ultramancode.aiguardrail.common.util.TraceContentPolicy;
import com.ultramancode.aiguardrail.guardrail.application.exception.GuardrailDetectorUnavailableException;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.guardrail.application.support.GuardrailDetectorFailurePolicySupport;
import com.ultramancode.aiguardrail.guardrail.application.support.GuardrailPromptSupport;
import com.ultramancode.aiguardrail.guardrail.application.support.GuardrailVerdictSupport;
import com.ultramancode.aiguardrail.guardrail.domain.SafetyVerdict;
import com.ultramancode.aiguardrail.prompt.application.port.out.PromptPort;
import com.ultramancode.aiguardrail.prompt.domain.PromptConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OutputSafetyAdvisor implements CallAdvisor {

    private static final String FALLBACK_OUTPUT_CHECK_PROMPT = """
            당신은 AI 출력 안전성 분류기입니다.
            
            아래 AI 응답을 분석하고 다음 항목 중 하나라도 해당하면 "UNSAFE"로 판정하세요.
            1. 혐오, 폭력, 성적 표현 등 부적절한 콘텐츠
            2. 마스킹되지 않은 민감정보 유출(카드번호, 주민번호 등)
            3. 근거 없는 비방 또는 브랜드/개인 명예훼손 발언
            4. 불법 행위 또는 위해 행위 방법 안내
            5. 보안 통제 우회(VPN/Proxy/Tor/Tunneling) 관련 안내/조언
            
            참고:
            - [PERSON_1], [PHONE_NUMBER_1] 같은 토큰은 정상 마스킹 값이므로 SAFE로 간주하세요.
            
            분석 대상 응답:
            "{output}"
            
            반드시 JSON만 반환하세요.
            반환 규칙:
            - verdict: "SAFE" 또는 "UNSAFE"
            - reason: 판정 근거 한 줄
            """;

    private final GuardrailVerdictSupport guardrailVerdictSupport;
    private final PromptPort promptPort;
    private final PiiUseCase piiUseCase;

    @Value("${guardrail.output.prompt-name:" + PromptConstants.PROMPT_OUTPUT_SAFETY + "}")
    private String promptName;

    @Value("${guardrail.output.on-detector-error:${guardrail.output.allow-response-when-safety-check-fails:${guardrail.output.fail-open:false}}}")
    private String detectorErrorPolicy;

    @Value("${guardrail.output.vendor:" + LlmConstants.VENDOR_GOOGLE + "}")
    private String outputGuardVendor;

    @Value("${guardrail.pii.trace-raw-content:false}")
    private boolean traceRawContent;

    public OutputSafetyAdvisor(
            GuardrailVerdictSupport guardrailVerdictSupport,
            PromptPort promptPort,
            PiiUseCase piiUseCase
    ) {
        this.guardrailVerdictSupport = guardrailVerdictSupport;
        this.promptPort = promptPort;
        this.piiUseCase = piiUseCase;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);

        String llmOutput = ChatResponseTextExtractor.extract(response);
        if (llmOutput == null || llmOutput.isBlank()) {
            return response;
        }

        String traceOutput = resolveTraceOutput(llmOutput);
        log.info("[OUTPUT-GUARD] 출력 안전성 검사 대상: \"{}\"", truncateForLog(traceOutput, 100));

        SafetyCheckOutcome safetyCheck = evaluateSafety(llmOutput);
        if (safetyCheck.error() != null) {
            if (shouldAllowOnSafetyCheckFailure("[OUTPUT-GUARD]", safetyCheck.error())) {
                return response;
            }
            throw new GuardrailDetectorUnavailableException(
                    "Guardrail detector is temporarily unavailable.",
                    safetyCheck.error()
            );
        }

        if (safetyCheck.verdict() != null && safetyCheck.verdict().isUnsafe()) {
            logUnsafeOutput("[OUTPUT-GUARD]", traceOutput, safetyCheck.verdict());
            throw new SecurityException("Request blocked due to safety policy violation.");
        }

        logSafeOutput("[OUTPUT-GUARD]", safetyCheck.verdict());
        return response;
    }

    @Override
    public String getName() {
        return "OutputSafetyAdvisor";
    }

    @Override
    public int getOrder() {
        return 2;
    }

    private SafetyCheckOutcome evaluateSafety(String llmOutput) {
        try {
            String systemPrompt = PromptTemplateSanitizer.sanitize(fetchPromptContent(), "output");
            SafetyVerdict verdict = guardrailVerdictSupport.classify(
                    outputGuardVendor,
                    null,
                    systemPrompt,
                    llmOutput,
                    "Output safety classifier"
            );
            return new SafetyCheckOutcome(verdict, null);
        } catch (RuntimeException e) {
            return new SafetyCheckOutcome(null, e);
        }
    }

    private boolean shouldAllowOnSafetyCheckFailure(String logPrefix, RuntimeException e) {
        boolean allowOnFailure = GuardrailDetectorFailurePolicySupport.shouldAllowOnDetectorFailure(
                detectorErrorPolicy,
                logPrefix,
                log
        );
        if (allowOnFailure) {
            log.error("{} 출력 검사 LLM 오류: {}. 정책에 따라 응답을 통과시킵니다.", logPrefix, e.getMessage(), e);
            return true;
        }

        log.error("{} 출력 검사 LLM 오류: {}. 정책에 따라 응답을 차단합니다.", logPrefix, e.getMessage(), e);
        return false;
    }

    private void logUnsafeOutput(String logPrefix, String llmOutput, SafetyVerdict verdict) {
        log.warn("{} 안전하지 않은 출력 감지. reason={}", logPrefix, verdict.reason());
        log.warn("{} 차단된 출력: \"{}\"", logPrefix, truncateForLog(llmOutput, 200));
    }

    private void logSafeOutput(String logPrefix, SafetyVerdict verdict) {
        String reason = "No details";
        if (verdict != null) {
            reason = verdict.reason();
        }

        log.info("{} 출력이 안전하다고 판정했습니다. reason={}", logPrefix, reason);
    }

    private String truncateForLog(String text, int maxLength) {
        if (text == null) {
            return "null";
        }

        String sanitized = text.replace('\n', ' ').replace('\r', ' ');
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }

        return sanitized.substring(0, maxLength) + "... (truncated)";
    }

    private String fetchPromptContent() {
        return GuardrailPromptSupport.fetchPromptContentOrFallback(
                promptPort,
                promptName,
                FALLBACK_OUTPUT_CHECK_PROMPT,
                "[OUTPUT-GUARD]",
                "output safety",
                log
        );
    }

    private String resolveTraceOutput(String rawOutput) {
        return TraceContentPolicy.resolve(rawOutput, traceRawContent, piiUseCase::tokenizeWithoutObservation);
    }

    private record SafetyCheckOutcome(SafetyVerdict verdict, RuntimeException error) {
    }
}
