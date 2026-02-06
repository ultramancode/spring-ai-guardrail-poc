package com.ultramancode.aiguardrail.guardrail.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuardrailResponse(
        String input,
        String output,
        Status status,
        String reason
) {
    public enum Status {
        SUCCESS,
        BLOCKED,
        ERROR
    }

    public static GuardrailResponse success(String input, String output) {
        return new GuardrailResponse(input, output, Status.SUCCESS, null);
    }

    public static GuardrailResponse blocked(String input, String reason) {
        return new GuardrailResponse(input, "Request blocked.", Status.BLOCKED, reason);
    }

    public static GuardrailResponse error(String input, String errorMessage) {
        return new GuardrailResponse(input, errorMessage, Status.ERROR, "INTERNAL_ERROR");
    }
}
