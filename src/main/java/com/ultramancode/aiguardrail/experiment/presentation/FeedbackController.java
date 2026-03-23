package com.ultramancode.aiguardrail.experiment.presentation;

import com.ultramancode.aiguardrail.common.observability.command.RecordScoreCommand;
import com.ultramancode.aiguardrail.experiment.application.port.in.SubmitFeedbackUseCase;
import com.ultramancode.aiguardrail.experiment.presentation.mapper.FeedbackMapper;
import com.ultramancode.aiguardrail.experiment.presentation.request.UserFeedbackRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 피드백 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final SubmitFeedbackUseCase submitFeedbackUseCase;

    @PostMapping
    public ResponseEntity<Void> submitFeedback(@RequestBody @Valid UserFeedbackRequest request) {
        log.info("[FEEDBACK] Received feedback for trace: {}, value: {}", request.getTraceId(), request.getValue());

        RecordScoreCommand command = FeedbackMapper.toCommand(request);
        submitFeedbackUseCase.submitFeedback(command);

        return ResponseEntity.ok().build();
    }
}
