package com.ultramancode.aiguardrail.guardrail.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuardrailResponse(
        String input,
        String output,
        Status status,
        String reason
) {
    public static GuardrailResponse success(String input, String output) {
        return new GuardrailResponse(input, output, Status.SUCCESS, null);
    }

    public enum Status {
        SUCCESS
    }
}
