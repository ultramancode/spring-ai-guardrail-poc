package com.ultramancode.aiguardrail.experiment.application.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * LLM-as-a-Judge 평가 결과
 * LLM이 두 텍스트(기대값 vs 실제값)의 의미적 유사도를 평가한 결과입니다.
 * 0~1 사이의 연속적인 점수와 판단 근거를 포함합니다.
 */
public record SimilarityScore(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Semantic similarity score between 0.0 (completely different) and 1.0 (identical meaning)")
        double score,

        @JsonProperty
        @JsonPropertyDescription("Brief explanation for the similarity score")
        String reason
) {
    /**
     * 주어진 임계값 이상이면 "통과"로 판정
     *
     * @param threshold 통과 기준 점수 (예: 0.7)
     */
    public boolean passes(double threshold) {
        return score >= threshold;
    }

    /**
     * 기본 임계값(0.7) 기준 통과 여부
     */
    public boolean passes() {
        return passes(0.7);
    }
}
