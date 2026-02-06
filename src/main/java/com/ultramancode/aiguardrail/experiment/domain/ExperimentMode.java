package com.ultramancode.aiguardrail.experiment.domain;

/**
 * 실험 실행 모드
 */
public enum ExperimentMode {
    /**
     * 프롬프트 튜닝용: 데이터셋의 텍스트만 사용하여 LLM 호출
     */
    PROMPT_ONLY,

    /**
     * 워크플로우 재현용: 실제 유스케이스(PDF/이미지 분석 등) 로직 전체 실행
     */
    FULL_WORKFLOW
}
