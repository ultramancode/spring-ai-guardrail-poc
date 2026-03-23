package com.ultramancode.aiguardrail.guardrail.application.port.in;

import com.ultramancode.aiguardrail.guardrail.application.command.PiiChatCommand;
import com.ultramancode.aiguardrail.guardrail.application.result.PiiChatResult;

/**
 * 개인정보 보호가 적용된 채팅 서비스의 유스케이스 인터페이스입니다.
 * 사용자의 질문에서 개인정보를 마스킹하여 모델에 전달하고, 모델의 답변 내에 포함된 개인정보를 복구하여 반환합니다.
 */
public interface PiiChatUseCase {
    /**
     * 개인정보 보호가 적용된 채팅을 수행합니다.
     *
     * @param command 채팅 요청 정보 (질문, 모델 설정 등)
     * @return 개인정보가 복구된 최종 답변 및 관측 메타데이터
     */
    PiiChatResult chat(PiiChatCommand command);
}
