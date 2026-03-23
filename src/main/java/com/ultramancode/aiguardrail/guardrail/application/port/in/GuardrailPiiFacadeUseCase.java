package com.ultramancode.aiguardrail.guardrail.application.port.in;

/**
 * 외부 모듈에서 PII 토큰화를 호출하기 위한 파사드 유스케이스입니다.
 */
public interface GuardrailPiiFacadeUseCase {

    String tokenizeWithoutObservation(String text);

    void clearContext();
}
