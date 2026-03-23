package com.ultramancode.aiguardrail.experiment.presentation.request;

import com.ultramancode.aiguardrail.common.observability.domain.ScoreType;
import com.ultramancode.aiguardrail.experiment.domain.model.ExperimentDefaults;
import com.ultramancode.aiguardrail.experiment.domain.model.ExperimentGuardrailMode;
import com.ultramancode.aiguardrail.experiment.domain.model.ExperimentMode;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;

/**
 * 실험 실행 요청 DTO.
 */
@Data
@Builder
public class ExperimentRequest {

    /**
     * Langfuse dataset 이름.
     */
    private String datasetName;

    /**
     * 실행 이름(run name). 비어 있으면 서버에서 자동 생성한다.
     */
    private String runName;

    /**
     * 결과 표시에 사용할 모델 라벨.
     */
    private String modelLabel;

    /**
     * 실행에 사용할 LLM 벤더.
     */
    private String vendor;

    /**
     * 실행에 사용할 LLM 모델명.
     */
    private String model;

    /**
     * 실행 모드.
     * FULL_WORKFLOW 또는 TARGET_ONLY.
     * 하위 호환을 위해 PROMPT_ONLY 입력도 허용한다.
     */
    @Builder.Default
    private String mode = ExperimentMode.FULL_WORKFLOW.getValue();

    /**
     * 실험 대상.
     * ANALYZE_PDF 또는 ANALYZE_IMAGE.
     */
    private String target;

    /**
     * 실행 라벨.
     */
    private String label;

    /**
     * Dataset 필드 매핑.
     */
    @Builder.Default
    @Valid
    private FieldMapping fieldMapping = FieldMapping.builder().build();

    /**
     * 평가 설정.
     */
    @Builder.Default
    @Valid
    private EvaluationConfig evaluation = EvaluationConfig.builder().build();

    /**
     * 프롬프트 설정.
     */
    @Builder.Default
    @Valid
    private PromptConfig prompt = PromptConfig.builder().build();

    /**
     * FULL_WORKFLOW 모드에서 사용할 가드레일 체인.
     * injection, output, full 중 하나.
     */
    @Builder.Default
    private String targetGuardrail = ExperimentGuardrailMode.FULL.getValue();

    /**
     * Langfuse score 이름.
     */
    @Builder.Default
    private String scoreName = ScoreType.EXPERIMENT_SCORE.getValue();

    @Data
    @Builder
    public static class FieldMapping {

        /**
         * 입력 질문 필드명.
         */
        @Builder.Default
        private String input = ExperimentDefaults.DEFAULT_INPUT_FIELD;

        /**
         * 기대 결과(정답) 필드명.
         */
        @Builder.Default
        private String expected = ExperimentDefaults.DEFAULT_EXPECTED_FIELD;

        /**
         * 기대 사유 필드명.
         */
        private String expectedReason;
    }

    @Data
    @Builder
    public static class EvaluationConfig {

        /**
         * 평가 방식.
         * exact_match(완전 일치), contains(포함), llm_judge(LLM 판정).
         */
        @Builder.Default
        private String type = ExperimentDefaults.DEFAULT_EVALUATION_TYPE;

        /**
         * llm_judge 임계값.
         */
        @Builder.Default
        private double threshold = ExperimentDefaults.DEFAULT_THRESHOLD;

        /**
         * reason 유사도 평가 여부.
         */
        @Builder.Default
        private boolean evaluateReason = true;

        /**
         * 비교 모드.
         * masked_only(마스킹 응답만), detokenized_only(복원 응답만), masked_then_detokenized(순차 비교).
         */
        @Builder.Default
        private String comparisonMode = ExperimentDefaults.DEFAULT_COMPARISON_MODE;
    }

    @Data
    @Builder
    public static class PromptConfig {

        /**
         * Langfuse prompt 이름.
         */
        private String name;

        /**
         * prompt 버전 표기.
         * 현재 POC에서는 메타 정보로만 사용한다.
         */
        private String version;

        /**
         * 인라인 system prompt.
         * 지정 시 name보다 우선한다.
         */
        private String systemPrompt;
    }
}
