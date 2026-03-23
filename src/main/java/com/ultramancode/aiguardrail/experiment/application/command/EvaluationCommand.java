package com.ultramancode.aiguardrail.experiment.application.command;

import com.ultramancode.aiguardrail.experiment.domain.model.EvaluationMethod;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EvaluationCommand {
    /**
     * 데이터셋의 기대 결과값
     */
    private final String expected;

    /**
     * LLM의 실제 응답값
     */
    private final String actual;

    /**
     * 평가 방식 (EXACT_MATCH, CONTAINS, LLM_JUDGE)
     */
    private final EvaluationMethod method;

    /**
     * 유사도 측정 임계값 (LLM Judge 사용 시)
     */
    @Builder.Default
    private final double threshold = 0.7;
}
