package com.ultramancode.aiguardrail.experiment.application.port.out;

/**
 * 실험 모듈에서 사용하는 PII 토큰화 포트입니다.
 */
public interface ExperimentPiiPort {

    String tokenize(String text);

    void clearContext();
}

