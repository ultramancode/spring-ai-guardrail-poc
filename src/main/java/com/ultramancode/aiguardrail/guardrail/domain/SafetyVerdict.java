package com.ultramancode.aiguardrail.guardrail.domain;

/**
 * 가드레일/정책 검사 결과 (Domain Model)
 * [책임]
 * - 안전성 검사 결과(PASS/FAIL)와 판단 근거를 담는 불변 객체
 * - LLM 응답이 안전 정책을 위반했는지 판별하는 도메인 모델
 */
public record SafetyVerdict(
        Verdict verdict,
        String reason
) {
    public boolean isUnsafe() {
        return verdict == Verdict.UNSAFE;
    }

    public enum Verdict {
        SAFE,
        UNSAFE
    }
}
