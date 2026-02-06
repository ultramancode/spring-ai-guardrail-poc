package com.ultramancode.aiguardrail.experiment.presentation;

import com.ultramancode.aiguardrail.experiment.application.result.ExperimentResult;
import com.ultramancode.aiguardrail.experiment.application.command.RunExperimentCommand;
import com.ultramancode.aiguardrail.experiment.application.result.HumanEvaluationResult;
import com.ultramancode.aiguardrail.experiment.application.port.in.RunExperimentUseCase;
import com.ultramancode.aiguardrail.experiment.presentation.request.ExperimentRequest;
import com.ultramancode.aiguardrail.experiment.presentation.mapper.ExperimentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 실험(Experiment) API 컨트롤러
 * <p>
 * Langfuse 데이터셋을 기반으로 가드레일/LLM 성능을 테스트하는 API를 제공합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/experiment")
@RequiredArgsConstructor
public class ExperimentController {

    private final RunExperimentUseCase runExperimentUseCase;

    @PostMapping("/run")
    public ResponseEntity<ExperimentResult> runExperiment(@RequestBody ExperimentRequest request) {
        log.info("[API] POST /api/experiment/run - Dataset: {}, Run: {}",
                request.getDatasetName(), request.getRunName());

        // 필수 파라미터 검증
        if (request.getDatasetName() == null || request.getDatasetName().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Use Mapper
        RunExperimentCommand command = ExperimentMapper.toCommand(request);

        ExperimentResult result = runExperimentUseCase.runExperiment(command);

        log.info("[API] Experiment completed: {}/{} passed ({}%)",
                result.getPassed(), result.getTotal(), Math.round(result.getAccuracy() * 100));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/quick")
    public ResponseEntity<ExperimentResult> quickExperiment(
            @RequestParam("dataset") String datasetName,
            @RequestParam(value = "run", defaultValue = "quick-test") String runName,
            @RequestParam(value = "prompt", required = false) String promptName) {

        log.info("[API) GET /api/experiment/quick - Dataset: {}", datasetName);

        ExperimentRequest request = ExperimentRequest.builder()
                .datasetName(datasetName)
                .runName(runName)
                .prompt(ExperimentRequest.PromptConfig.builder()
                        .name(promptName)
                        .build())
                .fieldMapping(ExperimentRequest.FieldMapping.builder()
                        .input("input")
                        .expected("verdict")
                        .build())
                .evaluation(ExperimentRequest.EvaluationConfig.builder()
                        .type("exact_match")
                        .build())
                .scoreName("quick-test-score")
                .build();

        // Use Mapper
        RunExperimentCommand command = ExperimentMapper.toCommand(request);

        ExperimentResult result = runExperimentUseCase.runExperiment(command);

        return ResponseEntity.ok(result);
    }

    /**
     * 헬스체크 / 서비스 상태 확인
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Experiment API is ready");
    }

    // ============================================================
    // Human Annotation Score 집계 API
    // ============================================================

    /**
     * Human Annotation 점수와 LLM 자동 평가 점수를 비교 분석
     * <p>
     * [사용 시나리오]
     * 1. Dataset Run 실행 후 LLM이 자동 채점
     * 2. 팀원이 Langfuse UI에서 일부 케이스를 Human Annotation
     * 3. 이 API로 LLM vs Human 일치율 분석
     * <p>
     * [요청 예시]
     * GET /api/experiment/human-scores?runName=guardrail-v1&humanScoreName=human-eval&llmScoreName=experiment-score
     * <p>
     * [응답 예시]
     * {
     * "runName": "guardrail-v1",
     * "humanTotalCount": 10,
     * "llmTotalCount": 50,
     * "agreementRate": 0.92,
     * "disagreements": {"trace-123": "LLM=PASS, Human=FAIL"}
     * }
     */
    @GetMapping("/human-scores")
    public ResponseEntity<HumanEvaluationResult> aggregateHumanScores(
            @RequestParam("runName") String runName,
            @RequestParam(value = "humanScoreName", defaultValue = "human-eval") String humanScoreName,
            @RequestParam(value = "llmScoreName", defaultValue = "experiment-score") String llmScoreName) {

        log.info("[API] GET /api/experiment/human-scores - Run: {}, Human: {}, LLM: {}",
                runName, humanScoreName, llmScoreName);

        var result = runExperimentUseCase.aggregateHumanScores(runName, humanScoreName, llmScoreName);

        log.info("[API] Human vs LLM agreement: {}%", Math.round(result.agreementRate() * 100));

        return ResponseEntity.ok(result);
    }
}
