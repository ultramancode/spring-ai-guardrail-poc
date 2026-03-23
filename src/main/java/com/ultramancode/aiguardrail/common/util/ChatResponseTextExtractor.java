package com.ultramancode.aiguardrail.common.util;

import org.springframework.ai.chat.client.ChatClientResponse;

/**
 * ChatClientResponse에서 출력 텍스트를 안전하게 추출하는 유틸리티입니다.
 */
public final class ChatResponseTextExtractor {

    private ChatResponseTextExtractor() {
    }

    public static String extract(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null) {
            return null;
        }
        if (response.chatResponse().getResult() == null) {
            return null;
        }
        if (response.chatResponse().getResult().getOutput() == null) {
            return null;
        }
        return response.chatResponse().getResult().getOutput().getText();
    }
}
