package com.ultramancode.aiguardrail.experiment.presentation.request;

import lombok.Builder;
import lombok.Data;

/**
 * 실험(Experiment) 실행 요청 DTO
 * <p>
 * Langfuse 데이터셋을 기반으로 가드레일/LLM 성능을 테스트하기 위한 요청 객체입니다.
 * 다양한 평가 시나리오(가드레일 테스트, 모델 비교, 프롬프트 A/B 테스트 등)에 범용적으로 사용됩니다.
 * <p>
 * [구조]
 * - 기본 설정: datasetName, runName 등 필수 정보
 * - fieldMapping: Dataset 필드명 매핑 (확장 가능)
 * - evaluation: 평가 방식 및 임계값 설정 (확장 가능)
 * - prompt: 프롬프트 설정 (Langfuse 연동)
 */
@Data
@Builder
public class ExperimentRequest {

    // ============================================================
    // 1. 필수 설정
    // ============================================================

    /**
     * Langfuse에 등록된 데이터셋 이름
     * 예: "financial-advice-test", "jailbreak-attacks"
     */
    private String datasetName;

    /**
     * 실험 실행 이름 (Langfuse에서 비교/분석용)
     * 예: "v2-prompt-test-20260121", "gemini-vs-gpt"
     */
    private String runName;

    /**
     * 모델 라벨 (실제 모델 전환 X, 기록/비교용 태그)
     * 예: "gemini-2.0-flash", "gpt-4o", "claude-3"
     */
    private String modelLabel;

    /**
     * 실험 실행 모드 (PROMPT_ONLY, FULL_WORKFLOW)
     */
    @Builder.Default
    private String mode = "PROMPT_ONLY";

    /**
     * 실험 대상 서비스 (ANALYZE_PDF, ANALYZE_IMAGE 등)
     */
    private String target;

    /**
     * 실험 그룹 식별 라벨 (Langfuse 메타데이터에 기록)
     */
    private String label;

    // ============================================================
    // 2. 필드 매핑 (Dataset 필드명 → 테스트 입력/출력 매핑)
    // ============================================================

    /**
     * Dataset 필드와 테스트 변수 간의 매핑 설정
     * 다른 Dataset 구조에도 코드 수정 없이 적용 가능
     */
    @Builder.Default
    private FieldMapping fieldMapping = FieldMapping.builder().build();

    // ============================================================
    // 3. 평가 설정 (Evaluation Configuration)
    // ============================================================

    /**
     * 평가 방식, 임계값, 추가 평가 옵션 설정
     */
    @Builder.Default
    private EvaluationConfig evaluation = EvaluationConfig.builder().build();

    // ============================================================
    // 4. 프롬프트 설정 (Langfuse Prompt Management)
    // ============================================================

    /**
     * 프롬프트 관련 설정 (Langfuse 연동 or 직접 입력)
     */
    @Builder.Default
    private PromptConfig prompt = PromptConfig.builder().build();

    // ============================================================
    // 5. 가드레일 타겟 설정
    // ============================================================

    /**
     * 테스트할 가드레일 타입 (우리 시스템 전용)
     * - "injection": PromptInjectionAdvisor 테스트
     * - "output": OutputSafetyAdvisor 테스트
     * - "full": 전체 파이프라인 테스트
     */
    @Builder.Default
    private String targetGuardrail = "injection";

    /**
     * Langfuse에 기록할 점수 이름
     * 예: "accuracy", "guardrail-pass-rate"
     */
    @Builder.Default
    private String scoreName = "experiment-score";

    // ============================================================
    // 중첩 DTO: 필드 매핑
    // ============================================================

    @Data
    @Builder
    public static class FieldMapping {
        /**
         * 데이터셋에서 입력값을 가져올 필드명
         * 예: "input", "question", "user_query"
         */
        @Builder.Default
        private String input = "input";

        /**
         * 데이터셋에서 기대 결과를 가져올 필드명
         * 예: "verdict", "expected_output", "answer"
         */
        @Builder.Default
        private String expected = "verdict";

        /**
         * 기대 이유(reason)를 가져올 필드명 (선택)
         * Reason 품질 평가 시 사용
         * 예: "expected_reason", "reason_text"
         */
        private String expectedReason;
    }

    // ============================================================
    // 중첩 DTO: 평가 설정
    // ============================================================

    @Data
    @Builder
    public static class EvaluationConfig {
        /**
         * 평가 방식 (비교 로직)
         * - "exact_match": 정확히 일치하는지 비교 (기본값)
         * - "contains": 기대값이 실제 결과에 포함되는지
         * - "llm_judge": LLM-as-a-Judge 의미적 유사도 평가
         */
        @Builder.Default
        private String type = "exact_match";

        /**
         * llm_judge 평가 시 통과 임계값 (0.0 ~ 1.0)
         * 이 값 이상이면 PASS 처리
         */
        @Builder.Default
        private double threshold = 0.7;

        /**
         * Reason 품질 평가 활성화 여부
         * true인 경우 expectedReason과 actualReason을 LLM-as-a-Judge로 비교
         */
        @Builder.Default
        private boolean evaluateReason = true;
    }

    // ============================================================
    // 중첩 DTO: 프롬프트 설정
    // ============================================================

    @Data
    @Builder
    public static class PromptConfig {
        /**
         * Langfuse에 등록된 프롬프트 이름 (선택)
         * 이 값이 있으면 Langfuse에서 프롬프트를 가져와 사용합니다.
         * 예: "guardrail-system-prompt"
         */
        private String name;

        /**
         * 프롬프트 버전 또는 라벨 (선택)
         * 특정 버전을 지정하거나 "production", "latest" 등 라벨 사용
         */
        private String version;

        /**
         * 직접 입력하는 시스템 프롬프트 (name 대신 사용)
         * name보다 우선순위가 낮습니다.
         */
        private String systemPrompt;
    }
}
