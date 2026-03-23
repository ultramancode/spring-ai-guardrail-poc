package com.ultramancode.aiguardrail.prompt.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Langfuse 프롬프트 ID 관리
 * 애플리케이션에서 사용하는 Langfuse 프롬프트의 ID(이름) 상수를 관리합니다.
 * 이 값들은 @Value의 기본값으로 사용되거나, 설정이 없을 경우의 Fallback으로 사용됩니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PromptConstants {

    /**
     * PII 탐지 및 마스킹을 위한 시스템 프롬프트
     */
    public static final String PROMPT_PII_SYSTEM = "pii-system-prompt";

    /**
     * 프롬프트 인젝션(Jailbreak) 탐지를 위한 시스템 프롬프트
     */
    public static final String PROMPT_INJECTION_SYSTEM = "guardrail-system-prompt";

    /**
     * 실험 평가(LLM Judge)를 위한 시스템 프롬프트
     */
    public static final String PROMPT_EVALUATOR_SYSTEM = "experiment-evaluator-prompt";

    /**
     * 출력 안전성 검사를 위한 시스템 프롬프트
     */
    public static final String PROMPT_OUTPUT_SAFETY = "guardrail-output-safety-prompt";
}
