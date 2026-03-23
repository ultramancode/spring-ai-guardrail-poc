package com.ultramancode.aiguardrail.experiment.application.port.out;

import com.ultramancode.aiguardrail.common.observability.command.RecordScoreCommand;
import com.ultramancode.aiguardrail.experiment.application.result.ScoreResult;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 실험 평가/관측 백엔드(Langfuse) 연동 포트.
 */
public interface EvaluationRepositoryPort {

    /**
     * 실험 대상 dataset 항목 전체를 조회한다.
     */
    List<Map<String, Object>> fetchDatasetItems(String datasetName);

    /**
     * 실험 대상 dataset 항목을 페이지 단위로 조회한다.
     */
    List<Map<String, Object>> fetchDatasetItemsPage(String datasetName, int page, int limit);

    /**
     * dataset 조회 시 사용할 기본 페이지 크기를 반환한다.
     */
    int resolveDatasetItemsPageSize();

    /**
     * dataset 항목 수를 조회한다.
     * 기본 구현은 전체 조회 후 size를 계산한다.
     */
    default int countDatasetItems(String datasetName) {
        return fetchDatasetItems(datasetName).size();
    }

    /**
     * 특정 테스트 실행의 trace를 dataset 항목과 연결한다.
     */
    void linkDatasetRunItem(String runName, String datasetItemId, String traceId, @Nullable String observationId);

    /**
     * 평가 점수를 기록한다.
     */
    void recordScore(RecordScoreCommand command);

    /**
     * 집계를 위해 기록된 점수를 조회한다.
     */
    ScorePageResult fetchScores(int page, int limit);

    /**
     * 특정 runName에 연결된 traceId 목록을 조회한다.
     */
    Set<String> fetchTraceIdsByRunName(String runName);

    record ScorePageResult(List<ScoreResult> scores, int rawCount) {
        public static ScorePageResult empty() {
            return new ScorePageResult(List.of(), 0);
        }
    }
}
