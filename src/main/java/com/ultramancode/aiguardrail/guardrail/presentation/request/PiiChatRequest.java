package com.ultramancode.aiguardrail.guardrail.presentation.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PII 채팅 요청 DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PiiChatRequest {

    @NotBlank(message = "채팅 텍스트는 필수입니다.")
    private String text;

    private String vendor;
    private String model;
}
