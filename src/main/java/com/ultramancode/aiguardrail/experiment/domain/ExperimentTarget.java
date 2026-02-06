package com.ultramancode.aiguardrail.experiment.domain;

/**
 * 실험 대상 서비스 (유스케이스)
 */
public enum ExperimentTarget {
    /**
     * PDF 분석 서비스
     */
    ANALYZE_PDF,

    /**
     * 이미지 분석 서비스
     */
    ANALYZE_IMAGE,

    /**
     * PII 마스킹 서비스 (향후 확장용)
     */
    PII_MASKING
}
