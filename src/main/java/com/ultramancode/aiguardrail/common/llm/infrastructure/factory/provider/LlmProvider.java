package com.ultramancode.aiguardrail.common.llm.infrastructure.factory.provider;

import com.ultramancode.aiguardrail.common.llm.LlmFactoryRequest;
import org.springframework.ai.chat.model.ChatModel;

/**
 * LLM 벤더별 ChatModel 생성을 담당하는 Provider 인터페이스
 */
public interface LlmProvider {
    /**
     * 지원하는 벤더명을 반환합니다.
     */
    String getVendor();

    /**
     * 해당 벤더의 기본 모델명을 반환합니다.
     */
    String getDefaultModelName();

    /**
     * 요청에 따라 ChatModel을 생성합니다.
     */
    ChatModel createModel(LlmFactoryRequest request);
}
