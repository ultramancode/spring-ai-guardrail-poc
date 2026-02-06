package com.ultramancode.aiguardrail.experiment.application.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 실험(Experiment) 실행 결과 DTO (Application Layer)
 * <p>
 * 데이터셋 기반 테스트 실행 후 반환되는 결과 객체입니다.
 * 전체 통계와 개별 테스트 케이스 결과를 모두 포함합니다.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExperimentResult {

    private String runName;
    private String datasetName;
    private String promptInfo;
    private String modelLabel;

    private int total;
    private int passed;
    private int failed;
    private double accuracy;
    private long executionTimeMs;

    private List<TestCaseResult> details;

    private ConfusionMatrix confusionMatrix;
    private double f1Score;
    private Double averageReasonScore;

    @Data
    @Builder
    public static class ConfusionMatrix {
        private int truePositive;
        private int trueNegative;
        private int falsePositive;
        private int falseNegative;
        private double precision;
        private double recall;
    }

    @Data
    @Builder
    public static class TestCaseResult {
        private String input;
        private String expected;
        private String actual;
        private boolean match;
        private double score;
        private String traceId;
        private String observationId;
        private String errorMessage;
        private String expectedReason;
        private String actualReason;
        private Double reasonScore;
    }
}
