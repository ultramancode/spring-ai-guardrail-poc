package com.ultramancode.aiguardrail.observability.application.port.out;

import com.ultramancode.aiguardrail.multimodal.domain.GenerationAttachment;
import com.ultramancode.aiguardrail.common.observability.UsageResult;

public interface ObservabilityPort {
    /**
     * LLM 생성 작업 기록 (Unified)
     *
     * @param traceId    추적 ID
     * @param name       작업 이름 (예: "pdf-analysis", "vision-direct")
     * @param modelName  사용된 모델명
     * @param input      사용자 입력 (텍스트)
     * @param output     LLM 응답 (텍스트)
     * @param attachment 첨부 파일 (Option: null 가능)
     * @param usage      토큰 사용량 (Option: null 가능)
     * @param startTime  시작 시간
     * @param endTime    종료 시간
     */
    void recordGeneration(String traceId, String name, String modelName,
                          String input, String output,
                          GenerationAttachment attachment,
                          UsageResult usage,
                          long startTime, long endTime);

    /**
     * 현재 스팬에 입력 태그 추가
     */
    void traceInput(String input);

    /**
     * 현재 스팬에 출력 태그 추가
     */
    void traceOutput(String output);

    /**
     * Trace ID 유효성 검사
     */
    boolean isValidTraceId(String traceId);
}
