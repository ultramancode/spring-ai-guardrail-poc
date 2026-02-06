package com.ultramancode.aiguardrail.experiment.presentation.request;

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
    private String traceId;

    /**
     * 점수 (1: 좋아요, 0: 싫어요)
     */
    private Integer value;

    /**
     * 선택적 코멘트
     */
    private String comment;
}
