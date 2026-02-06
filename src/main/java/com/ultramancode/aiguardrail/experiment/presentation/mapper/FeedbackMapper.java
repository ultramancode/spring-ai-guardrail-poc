package com.ultramancode.aiguardrail.experiment.presentation.mapper;

import com.ultramancode.aiguardrail.common.observability.RecordScoreCommand;
import com.ultramancode.aiguardrail.experiment.presentation.request.UserFeedbackRequest;

/**
 * 피드백 관련 매핑 유틸리티
 */
public class FeedbackMapper {

    public static RecordScoreCommand toCommand(UserFeedbackRequest request) {
        return RecordScoreCommand.builder()
                .traceId(request.getTraceId())
                .scoreName("user_feedback")
                .value(request.getValue()) // 1 or 0
                .comment(request.getComment())
                .build();
    }
}
