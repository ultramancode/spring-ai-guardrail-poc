package com.ultramancode.aiguardrail.common.pii.port.out;

/**
 * 멀티모달 모듈 내에서 PII(개인정보) 처리를 추상화하는 포트입니다.
 * 애플리케이션 레이어는 이 인터페이스에 의존하며, 구체적인 구현(가드레일 서비스 호출 등)은 인프라 레이어에서 담당합니다.
 */
public interface PiiProcessingPort {
    /**
     * 입력을 토큰화(마스킹) 처리합니다.
     */
    String tokenize(String text);

    /**
     * 토큰화된 내용을 원본으로 복구(디토큰화)합니다.
     */
    String detokenize(String text);
}
