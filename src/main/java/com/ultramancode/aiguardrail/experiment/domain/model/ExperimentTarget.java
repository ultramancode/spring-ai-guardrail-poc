package com.ultramancode.aiguardrail.experiment.domain.model;

/**
 * 실험 대상 서비스 (유스케이스)
 */
public enum ExperimentTarget {

    ANALYZE_PDF,

    ANALYZE_IMAGE;

    public static ExperimentTarget fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Experiment target must not be blank.");
        }

        for (ExperimentTarget target : ExperimentTarget.values()) {
            if (target.name().equalsIgnoreCase(value.trim())) {
                return target;
            }
        }

        throw new IllegalArgumentException("Unsupported experiment target: " + value);
    }
}
