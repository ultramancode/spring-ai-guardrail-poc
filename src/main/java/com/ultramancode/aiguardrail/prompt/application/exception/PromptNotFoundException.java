package com.ultramancode.aiguardrail.prompt.application.exception;

import com.ultramancode.aiguardrail.common.exception.AbstractApiMappedRuntimeException;

public class PromptNotFoundException extends AbstractApiMappedRuntimeException {

    private static final int HTTP_STATUS_CODE = 404;
    private static final String ERROR_CODE = "PROMPT_NOT_FOUND";
    private static final String TITLE = "프롬프트 미존재";
    private static final String DETAIL = "요청한 프롬프트를 찾을 수 없습니다.";
    private static final String TYPE = "https://ultramancode.com/errors/prompt-not-found";

    public PromptNotFoundException(String message) {
        super(
                HTTP_STATUS_CODE,
                ERROR_CODE,
                TITLE,
                DETAIL,
                TYPE,
                message
        );
    }

    public PromptNotFoundException(String message, Throwable cause) {
        super(
                HTTP_STATUS_CODE,
                ERROR_CODE,
                TITLE,
                DETAIL,
                TYPE,
                message,
                cause
        );
    }
}
