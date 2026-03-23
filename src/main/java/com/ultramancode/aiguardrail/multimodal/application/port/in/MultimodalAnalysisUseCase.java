package com.ultramancode.aiguardrail.multimodal.application.port.in;

import com.ultramancode.aiguardrail.multimodal.application.command.MultimodalAnalysisCommand;
import com.ultramancode.aiguardrail.multimodal.application.result.MultimodalAnalysisResult;
import com.ultramancode.aiguardrail.multimodal.domain.MultimodalTargetType;

/**
 * 멀티모달 분석 유스케이스 공통 인터페이스.
 */
public interface MultimodalAnalysisUseCase {

    /**
     * 분석 명령을 실행한다.
     */
    MultimodalAnalysisResult analyze(MultimodalAnalysisCommand command);

    /**
     * 유스케이스가 처리하는 분석 대상 타입을 반환한다.
     */
    MultimodalTargetType getTargetType();
}
