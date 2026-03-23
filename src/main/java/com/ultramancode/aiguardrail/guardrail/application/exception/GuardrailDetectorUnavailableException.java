package com.ultramancode.aiguardrail.guardrail.application.exception;

import com.ultramancode.aiguardrail.common.exception.AbstractApiMappedRuntimeException;

/**
 * 가드레일 detector를 사용할 수 없을 때 발생하는 예외입니다.
 */
public class GuardrailDetectorUnavailableException extends AbstractApiMappedRuntimeException {

    private static final int HTTP_STATUS_CODE = 503;
    private static final String ERROR_CODE = "GUARDRAIL_DETECTOR_UNAVAILABLE";
    private static final String TITLE = "가드레일 감지기 장애";
    private static final String DETAIL = "가드레일 감지기에 일시적인 장애가 발생했습니다.";
    private static final String TYPE = "https://ultramancode.com/errors/guardrail-detector-unavailable";

    public GuardrailDetectorUnavailableException(String message) {
        super(
                HTTP_STATUS_CODE,
                ERROR_CODE,
                TITLE,
                DETAIL,
                TYPE,
                message
        );
    }

    public GuardrailDetectorUnavailableException(String message, Throwable cause) {
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
