package com.ultramancode.aiguardrail.prompt.application.exception;

import com.ultramancode.aiguardrail.common.exception.AbstractApiMappedRuntimeException;

public class PromptFetchFailedException extends AbstractApiMappedRuntimeException {

    private static final int HTTP_STATUS_CODE = 503;
    private static final String ERROR_CODE = "PROMPT_FETCH_FAILED";
    private static final String TITLE = "프롬프트 조회 실패";
    private static final String DETAIL = "프롬프트 조회 중 오류가 발생했습니다.";
    private static final String TYPE = "https://ultramancode.com/errors/prompt-fetch-failed";

    public PromptFetchFailedException(String message) {
        super(
                HTTP_STATUS_CODE,
                ERROR_CODE,
                TITLE,
                DETAIL,
                TYPE,
                message
        );
    }

    public PromptFetchFailedException(String message, Throwable cause) {
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
