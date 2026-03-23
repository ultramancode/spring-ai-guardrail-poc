package com.ultramancode.aiguardrail.experiment.application.result;

import lombok.Builder;

import java.util.Map;

/**
 * Human Annotation 점수 집계 결과 DTO
 * Langfuse에서 수집한 Human Annotation 점수와
 * 실험 자동 평가 점수(점수명 기반)를 비교 분석한 결과입니다.
 */
@Builder
public record HumanEvaluationResult(
        // 1. 메타 정보
        String runName,
        String humanScoreName,
        String autoScoreName,

        // 2. Human Annotation 통계
        int humanTotalCount,
        int humanPositiveCount,
        int humanNegativeCount,

        // 3. 자동 평가 점수 통계 (Human 점수와 비교 가능한 trace 기준)
        int autoComparedCount,

        int autoPositiveCount,
        int autoNegativeCount,

        // 4. Human vs Auto 비교 분석
        double agreementRate,
        int disagreementCount,
        Map<String, String> disagreements,

        long executionTimeMs
) {
}
