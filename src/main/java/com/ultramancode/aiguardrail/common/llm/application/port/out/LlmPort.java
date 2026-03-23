package com.ultramancode.aiguardrail.common.llm.application.port.out;

import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM 서버와의 통신을 추상화하는 포트입니다.
 */
public interface LlmPort {
    /**
     * 특정 벤더와 모델 정보를 바탕으로 ChatClient를 생성하거나 반환합니다.
     */
    ChatClient getChatClient(String vendor, String model);

    /**
     * 시스템 기본 설정된 ChatClient를 반환합니다.
     */
    ChatClient getDefaultChatClient();

    /**
     * 특정 벤더의 현재 사용 중인 모델 이름을 반환합니다.
     */
    String getModelName(String vendor);

    /**
     * 기본 벤더의 모델 이름을 반환합니다.
     */
    String getDefaultModelName();
}
