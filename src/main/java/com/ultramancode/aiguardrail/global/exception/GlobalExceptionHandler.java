package com.ultramancode.aiguardrail.global.exception;

import com.ultramancode.aiguardrail.common.exception.AbstractApiMappedRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 전역 예외 처리기.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String SECURITY_VIOLATION_MESSAGE = "요청이 가드레일 정책 위반으로 차단되었습니다.";
    private static final String INVALID_REQUEST_MESSAGE = "잘못된 요청입니다.";
    private static final String INVALID_STATE_MESSAGE = "요청 처리 중 서버 상태 오류가 발생했습니다.";
    private static final String ERROR_CODE_INVALID_REQUEST = "INVALID_REQUEST";
    private static final String ERROR_CODE_GUARDRAIL_VIOLATION = "GUARDRAIL_VIOLATION";
    private static final String ERROR_CODE_INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    private static final int MAX_CAUSE_LENGTH = 300;

    @Value("${app.error.expose-cause:false}")
    private boolean exposeCause;

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String path = "unknown";
        if (request instanceof ServletWebRequest servletWebRequest) {
            path = servletWebRequest.getRequest().getRequestURI();
        }

        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String field = fieldError.getField();
            String message = fieldError.getDefaultMessage();
            if (message == null || message.isBlank()) {
                message = "잘못된 값입니다.";
            }
            errors.computeIfAbsent(field, ignored -> new ArrayList<>()).add(message);
        }

        log.warn("[EXCEPTION] Validation failed path={}, traceId={}, errors={}", path, resolveTraceId(), errors);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "요청 검증에 실패했습니다."
        );
        problemDetail.setTitle("잘못된 요청");
        problemDetail.setType(URI.create("https://ultramancode.com/errors/invalid-request"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", path);
        problemDetail.setProperty("traceId", resolveTraceId());
        problemDetail.setProperty("errorCode", ERROR_CODE_INVALID_REQUEST);
        appendCause(problemDetail, ex);
        problemDetail.setProperty("errors", errors);

        return handleExceptionInternal(ex, problemDetail, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleSecurityException(SecurityException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] SecurityException path={}, traceId={}, message={}",
                request.getRequestURI(), resolveTraceId(), ex.getMessage());
        return buildProblem(
                HttpStatus.FORBIDDEN,
                "가드레일 정책 위반",
                SECURITY_VIOLATION_MESSAGE,
                "https://ultramancode.com/errors/guardrail-violation",
                ERROR_CODE_GUARDRAIL_VIOLATION,
                ex,
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("[EXCEPTION] IllegalArgumentException path={}, traceId={}, message={}",
                request.getRequestURI(), resolveTraceId(), ex.getMessage());
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "잘못된 요청",
                INVALID_REQUEST_MESSAGE,
                "https://ultramancode.com/errors/invalid-request",
                ERROR_CODE_INVALID_REQUEST,
                ex,
                request
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalStateException(IllegalStateException ex, HttpServletRequest request) {
        log.error("[EXCEPTION] IllegalStateException path={}, traceId={}, message={}",
                request.getRequestURI(), resolveTraceId(), ex.getMessage(), ex);
        return buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "서버 상태 오류",
                INVALID_STATE_MESSAGE,
                "https://ultramancode.com/errors/internal-server-error",
                ERROR_CODE_INTERNAL_SERVER_ERROR,
                ex,
                request
        );
    }

    @ExceptionHandler(AbstractApiMappedRuntimeException.class)
    public ProblemDetail handleMappedException(AbstractApiMappedRuntimeException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getHttpStatusCode());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (status.is5xxServerError()) {
            log.error("[EXCEPTION] {} path={}, traceId={}, message={}",
                    ex.getClass().getSimpleName(),
                    request.getRequestURI(),
                    resolveTraceId(),
                    ex.getMessage(),
                    ex);
        } else {
            log.warn("[EXCEPTION] {} path={}, traceId={}, message={}",
                    ex.getClass().getSimpleName(),
                    request.getRequestURI(),
                    resolveTraceId(),
                    ex.getMessage());
        }

        return buildProblem(
                status,
                ex.getTitle(),
                ex.getDetail(),
                ex.getType(),
                ex.getErrorCode(),
                ex,
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("[EXCEPTION] Unexpected error path={}, traceId={}",
                request.getRequestURI(), resolveTraceId(), ex);
        return buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "내부 서버 오류",
                "예기치 못한 오류가 발생했습니다.",
                "https://ultramancode.com/errors/internal-server-error",
                ERROR_CODE_INTERNAL_SERVER_ERROR,
                ex,
                request
        );
    }

    private ProblemDetail buildProblem(
            HttpStatus status,
            String title,
            String detail,
            String type,
            String errorCode,
            Throwable throwable,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(type));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("path", request.getRequestURI());
        problemDetail.setProperty("traceId", resolveTraceId());
        problemDetail.setProperty("errorCode", errorCode);
        appendCause(problemDetail, throwable);
        return problemDetail;
    }

    private void appendCause(ProblemDetail problemDetail, Throwable throwable) {
        if (!exposeCause) {
            return;
        }
        problemDetail.setProperty("cause", resolveCauseMessage(throwable));
    }

    private String resolveCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }

        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            message = rootCause.getClass().getSimpleName();
        } else if (message.length() > MAX_CAUSE_LENGTH) {
            message = message.substring(0, MAX_CAUSE_LENGTH) + "...";
        }

        return rootCause.getClass().getSimpleName() + ": " + message;
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            return "unknown";
        }
        return traceId;
    }
}
