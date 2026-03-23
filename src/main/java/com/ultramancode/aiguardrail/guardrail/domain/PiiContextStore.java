package com.ultramancode.aiguardrail.guardrail.domain;

/**
 * PII 컨텍스트를 저장하고 관리하는 인터페이스입니다.
 * 도메인 레이어는 이 인터페이스에만 의존하며, 실제 저장 방식(ThreadLocal, 세션 등)은 인프라 레이어에서 결정합니다.
 */
public interface PiiContextStore {
    PiiContext get();

    void clear();
}
