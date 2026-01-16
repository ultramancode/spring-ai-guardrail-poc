package com.ultramancode.aiguardrail.presentation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleSecurityException(SecurityException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problemDetail.setTitle("Guardrail Violation");
        problemDetail.setType(URI.create("https://ultramancode.com/errors/guardrail-violation"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
