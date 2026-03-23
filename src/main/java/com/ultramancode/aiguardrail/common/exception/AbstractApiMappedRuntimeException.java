package com.ultramancode.aiguardrail.common.exception;

/**
 * 전역 에러 응답에 필요한 메타데이터를 담는 공통 런타임 예외입니다.
 */
public abstract class AbstractApiMappedRuntimeException extends RuntimeException implements ApiMappedException {

    private final int httpStatusCode;
    private final String errorCode;
    private final String title;
    private final String detail;
    private final String type;

    protected AbstractApiMappedRuntimeException(
            int httpStatusCode,
            String errorCode,
            String title,
            String detail,
            String type,
            String message
    ) {
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.errorCode = errorCode;
        this.title = title;
        this.detail = detail;
        this.type = type;
    }

    protected AbstractApiMappedRuntimeException(
            int httpStatusCode,
            String errorCode,
            String title,
            String detail,
            String type,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.httpStatusCode = httpStatusCode;
        this.errorCode = errorCode;
        this.title = title;
        this.detail = detail;
        this.type = type;
    }

    @Override
    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getDetail() {
        return detail;
    }

    @Override
    public String getType() {
        return type;
    }
}
