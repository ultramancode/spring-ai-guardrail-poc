package com.ultramancode.aiguardrail.experiment.presentation.mapper;

import com.ultramancode.aiguardrail.common.observability.command.RecordScoreCommand;
import com.ultramancode.aiguardrail.common.observability.domain.ScoreType;
import com.ultramancode.aiguardrail.experiment.presentation.request.UserFeedbackRequest;

/**
 * 피드백 요청을 스코어 커맨드로 변환합니다.
 */
public class FeedbackMapper {

    private FeedbackMapper() {
    }

    public static RecordScoreCommand toCommand(UserFeedbackRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("User feedback request must not be null.");
        }

        return RecordScoreCommand.builder()
                .traceId(request.getTraceId())
                .scoreName(ScoreType.USER_FEEDBACK.getValue())
                .value(request.getValue())
                .comment(request.getComment())
                .build();
    }
}
