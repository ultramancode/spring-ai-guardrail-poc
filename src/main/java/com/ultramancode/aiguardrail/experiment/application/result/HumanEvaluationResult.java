package com.ultramancode.aiguardrail.experiment.application.result;

import lombok.Builder;

import java.util.Map;

/**
 * Human Annotation 점수 집계 결과 DTO
 * <p>
 * Langfuse에서 수집한 Human Annotation 점수와
 * LLM-as-a-Judge 자동 점수를 비교 분석한 결과입니다.
 */
@Builder
public record HumanEvaluationResult(
        // 1. 메타 정보
        String runName,
        String scoreName,

        // 2. Human Annotation 통계
        int humanTotalCount,
        int humanPositiveCount,
        int humanNegativeCount,

        // 3. LLM-as-a-Judge 통계
        int llmTotalCount,
        int llmPositiveCount,
        int llmNegativeCount,

        // 4. Human vs LLM 비교 분석
        double agreementRate,
        int disagreementCount,
        Map<String, String> disagreements,

        long executionTimeMs
) {
}
