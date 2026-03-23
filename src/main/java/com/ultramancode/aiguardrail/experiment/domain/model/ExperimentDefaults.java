package com.ultramancode.aiguardrail.experiment.domain.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExperimentDefaults {

    public static final String DEFAULT_RUN_NAME_PREFIX = "experiment-";
    public static final String DEFAULT_INPUT_FIELD = "input";
    public static final String DEFAULT_EXPECTED_FIELD = "verdict";
    public static final String DEFAULT_EVALUATION_TYPE = "exact_match";
    public static final double DEFAULT_THRESHOLD = 0.7;
    public static final String DEFAULT_COMPARISON_MODE = "masked_only";
}
