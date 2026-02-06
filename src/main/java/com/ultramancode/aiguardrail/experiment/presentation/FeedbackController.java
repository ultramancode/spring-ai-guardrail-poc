package com.ultramancode.aiguardrail.experiment.presentation;

import com.ultramancode.aiguardrail.common.observability.RecordScoreCommand;

import com.ultramancode.aiguardrail.experiment.presentation.mapper.FeedbackMapper;
import com.ultramancode.aiguardrail.experiment.presentation.request.UserFeedbackRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 피드백(Feedback) API 컨트롤러 (Refactored)
 */
@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final com.ultramancode.aiguardrail.experiment.application.port.out.EvaluationRepositoryPort evaluationRepository;

    @PostMapping
    public ResponseEntity<Void> submitFeedback(@RequestBody UserFeedbackRequest request) {
        log.info("[FEEDBACK] Received feedback for trace: {}, value: {}", request.getTraceId(), request.getValue());

        // Use Mapper
        RecordScoreCommand command = FeedbackMapper.toCommand(request);

        evaluationRepository.recordScore(command);

        return ResponseEntity.ok().build();
    }
}
