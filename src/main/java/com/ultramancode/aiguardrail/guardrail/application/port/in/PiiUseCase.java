package com.ultramancode.aiguardrail.guardrail.application.port.in;

/**
 * 개인정보(PII) 보호와 관련된 유스케이스 인터페이스입니다.
 * 텍스트 내의 개인정보를 식별하여 토큰화(Masking)하거나, 토큰화된 정보를 다시 복구(Detokenize)하는 기능을 제공합니다.
 */
public interface PiiUseCase {
    /**
     * 텍스트 내의 개인정보를 토큰(가짜 정보)으로 대체합니다.
     *
     * @param text 원본 텍스트
     * @return 토큰화된 텍스트
     */
    String tokenize(String text);

    /**
     * 내부적인 토큰화 처리를 수행합니다.
     */
    String tokenizeWithoutObservation(String text);

    /**
     * 토큰화된 텍스트를 원본 정보로 복구합니다.
     *
     * @param text 토큰화된 텍스트
     * @return 복구된 원본 텍스트
     */
    String detokenize(String text);

    /**
     * 내부적인 복구 처리를 수행합니다.
     */
    String detokenizeWithoutObservation(String text);

    /**
     * 객체 구조를 재귀적으로 탐색하여 모든 토큰을 원본 정보로 복구합니다.
     *
     * @param input 토큰이 포함된 객체 (Map, List 등)
     * @return 복구된 객체
     */
    Object detokenizeRecursive(Object input);

    /**
     * 현재 요청/작업 단위의 PII 컨텍스트를 정리합니다.
     */
    void clearContext();
}
