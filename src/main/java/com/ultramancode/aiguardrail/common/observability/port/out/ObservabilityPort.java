package com.ultramancode.aiguardrail.common.observability.port.out;

import com.ultramancode.aiguardrail.common.observability.GenerationRecordResult;
import com.ultramancode.aiguardrail.common.observability.command.RecordGenerationCommand;
import com.ultramancode.aiguardrail.common.observability.command.RecordScoreCommand;

/**
 * 관측성 통합 포트 (Tracing, Recording, Scoring)
 */
public interface ObservabilityPort {

    /**
     * LLM 생성 작업 기록
     */
    GenerationRecordResult recordGeneration(RecordGenerationCommand command);

    /**
     * 평가 점수 기록
     */
    void recordScore(RecordScoreCommand command);

    /**
     * 현재 스팬에 입력 태그 추가
     */
    void traceInput(String input);

    /**
     * 현재 스팬에 출력 태그 추가
     */
    void traceOutput(String output);


}
