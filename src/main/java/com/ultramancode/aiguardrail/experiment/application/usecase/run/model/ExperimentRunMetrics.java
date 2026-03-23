package com.ultramancode.aiguardrail.experiment.application.usecase.run.model;

import com.ultramancode.aiguardrail.common.util.StringValueUtils;
import com.ultramancode.aiguardrail.experiment.application.result.ExperimentResult;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 실험 실행 중 상세 결과 샘플링과 집계 지표 누적을 담당합니다.
 */
@Getter
public class ExperimentRunMetrics {

    private final int maxResponseDetails;
    private final List<ExperimentResult.TestCaseResult> details;

    private boolean detailsSampled;
    private int totalCount;
    private int passedCount;
    private int errorCount;
    private int recordingErrorCount;
    private int truePositive;
    private int trueNegative;
    private int falsePositive;
    private int falseNegative;
    private double reasonScoreSum;
    private int reasonScoreCount;

    public ExperimentRunMetrics(int maxResponseDetails) {
        this.maxResponseDetails = maxResponseDetails;
        this.details = new ArrayList<>(Math.min(maxResponseDetails, 1024));
    }

    public void append(ExperimentResult.TestCaseResult detail, Predicate<String> unsafePredicate) {
        totalCount++;

        if (detail.isMatch()) {
            passedCount++;
        }

        if (StringValueUtils.asNonBlankString(detail.getErrorMessage()) != null) {
            errorCount++;
        } else {
            boolean expectedUnsafe = unsafePredicate.test(detail.getExpected());
            boolean actualUnsafe = unsafePredicate.test(resolveEvaluationActual(detail));

            if (expectedUnsafe && actualUnsafe) {
                truePositive++;
            } else if (!expectedUnsafe && !actualUnsafe) {
                trueNegative++;
            } else if (!expectedUnsafe) {
                falsePositive++;
            } else {
                falseNegative++;
            }
        }

        if (StringValueUtils.asNonBlankString(detail.getRecordingError()) != null) {
            recordingErrorCount++;
        }

        if (detail.getReasonScore() != null) {
            reasonScoreSum += detail.getReasonScore();
            reasonScoreCount++;
        }

        if (details.size() < maxResponseDetails) {
            details.add(detail);
        } else {
            detailsSampled = true;
        }
    }

    public Double averageReasonScoreOrNull() {
        if (reasonScoreCount <= 0) {
            return null;
        }
        return reasonScoreSum / reasonScoreCount;
    }

    private String resolveEvaluationActual(ExperimentResult.TestCaseResult detail) {
        String evaluationActual = detail.getEvaluationActual();
        if (StringValueUtils.asNonBlankString(evaluationActual) == null) {
            return detail.getActual();
        }
        return evaluationActual;
    }
}
