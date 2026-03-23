package com.ultramancode.aiguardrail.experiment.presentation;

import com.ultramancode.aiguardrail.experiment.application.command.AggregateHumanScoresCommand;
import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.application.port.in.AggregateHumanScoresUseCase;
import com.ultramancode.aiguardrail.experiment.application.port.in.RunExperimentUseCase;
import com.ultramancode.aiguardrail.experiment.application.result.ExperimentResult;
import com.ultramancode.aiguardrail.experiment.application.result.HumanEvaluationResult;
import com.ultramancode.aiguardrail.experiment.presentation.mapper.ExperimentMapper;
import com.ultramancode.aiguardrail.experiment.presentation.request.ExperimentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 실험 API 컨트롤러입니다.
 */
@Slf4j
@RestController
@Validated
@RequestMapping("/api/experiment")
@RequiredArgsConstructor
public class ExperimentController {

    private final RunExperimentUseCase runExperimentUseCase;
    private final AggregateHumanScoresUseCase aggregateHumanScoresUseCase;

    @PostMapping("/run")
    public ResponseEntity<ExperimentResult> runExperiment(@RequestBody @Valid ExperimentRequest request) {
        log.info("[API] POST /api/experiment/run - Dataset: {}, Run: {}",
                request.getDatasetName(), request.getRunName());

        RunExperimentCommand command = ExperimentMapper.toCommand(request);
        ExperimentResult result = runExperimentUseCase.runExperiment(command);

        log.info("[API] Experiment completed: {}/{} passed ({}%)",
                result.getPassed(), result.getTotal(), Math.round(result.getAccuracy() * 100));

        return ResponseEntity.ok(result);
    }

    /**
     * 헬스체크 API입니다.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Experiment API is ready");
    }

    /**
     * 사람 평가 점수와 자동 평가 점수를 비교 집계합니다.
     * 표준 파라미터는 autoScoreName이며,
     * 하위 호환을 위해 llmScoreName 파라미터도 동일 의미 alias로 허용합니다.
     */
    @GetMapping("/human-scores")
    public ResponseEntity<HumanEvaluationResult> aggregateHumanScores(
            @RequestParam("runName") String runName,
            @RequestParam(value = "humanScoreName", required = false) String humanScoreName,
            @RequestParam(value = "autoScoreName", required = false) String autoScoreName,
            @RequestParam(value = "llmScoreName", required = false) String legacyLlmScoreName) {
        AggregateHumanScoresCommand command = ExperimentMapper.toAggregateHumanScoresCommand(
                runName,
                humanScoreName,
                autoScoreName,
                legacyLlmScoreName
        );

        log.info("[API] GET /api/experiment/human-scores - Run: {}, Human: {}, Auto: {}",
                command.getRunName(), command.getHumanScoreName(), command.getAutoScoreName());

        HumanEvaluationResult result = aggregateHumanScoresUseCase.aggregateHumanScores(command);

        log.info("[API] Human vs Auto agreement: {}%", Math.round(result.agreementRate() * 100));

        return ResponseEntity.ok(result);
    }
}
