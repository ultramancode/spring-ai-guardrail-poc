package com.ultramancode.aiguardrail.experiment.presentation.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedbackRequest {
    /**
     * Feedback 대상 Trace ID (필수)
     */
    @NotBlank(message = "traceId is required")
    @Pattern(regexp = "^[0-9a-fA-F]{32}$", message = "traceId must be a 32-character hexadecimal string")
    private String traceId;

    /**
     * 점수 (1: 좋아요, 0: 싫어요)
     */
    @NotNull(message = "value is required")
    @Min(value = 0, message = "value must be 0 or 1")
    @Max(value = 1, message = "value must be 0 or 1")
    private Integer value;

    /**
     * 선택적 코멘트
     */
    private String comment;
}
