package com.ultramancode.aiguardrail.common.exception;

/**
 * 전역 예외 응답으로 변환 가능한 예외 메타데이터 계약입니다.
 */
public interface ApiMappedException {

    int getHttpStatusCode();

    String getErrorCode();

    String getTitle();

    String getDetail();

    String getType();
}
