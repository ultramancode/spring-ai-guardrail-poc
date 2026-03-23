package com.ultramancode.aiguardrail.guardrail.application.port.out;

import com.ultramancode.aiguardrail.guardrail.domain.PiiSpan;

import java.util.List;

/**
 * PII 분석 엔진을 위한 포트(Port) 인터페이스입니다.
 * 애플리케이션 계층(Service)과 인프라 계층(Phileas, Presidio 등) 사이의 결합도를 낮추는 역할을 합니다.
 */
public interface PiiAnalyzerPort {

    /**
     * 텍스트를 분석하여 감지된 개인정보(PII) 구간 목록을 반환합니다.
     *
     * @param text 분석할 입력 텍스트
     * @return 감지된 PII 구간(Span) 목록
     */
    List<PiiSpan> analyze(String text);
}
