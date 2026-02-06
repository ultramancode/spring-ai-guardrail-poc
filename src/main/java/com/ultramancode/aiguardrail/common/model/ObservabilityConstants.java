package com.ultramancode.aiguardrail.common.model;

/**
 * 모니터링 및 관측성을 위한 상수 관리
 */
public class ObservabilityConstants {

    // Scores
    public static final String SCORE_SECURITY_RISK = "security-risk";
    public static final String SCORE_PII_PROTECTION = "pii-protection";
    public static final String SCORE_EXPERIMENT = "experiment-score";

    // Trace Tags (Span Attributes)
    public static final String TAG_PII_DETOKENIZED = "pii.detokenized";
    public static final String TAG_TOOL_RESULT_MASKED = "tool.result.masked";
    public static final String TAG_AUDIT_DECRYPTED_ARGS = "audit.decrypted.args";
    public static final String TAG_AUDIT_DECRYPTED_RESULT = "audit.decrypted.result";

    // Experiment Trace Tags
    public static final String TAG_EXP_RUN_NAME = "experiment.run_name";
    public static final String TAG_DATASET_ITEM_ID = "dataset.item_id";
    public static final String TAG_CONTEXT_TYPE = "context.type";

    // Standard KV Tags for Observations (Micrometer)
    public static final String TAG_INPUT = "input";
    public static final String TAG_OUTPUT = "output";

    // GenAI Semantic Conventions (OTel)
    public static final String TAG_GEN_AI_PROMPT = "gen_ai.prompt";
    public static final String TAG_GEN_AI_COMPLETION = "gen_ai.completion";
    public static final String TAG_INPUT_VALUE = "input.value";
    public static final String TAG_OUTPUT_VALUE = "output.value";

    // Log Messages & Comments
    public static final String MSG_SAFETY_CHECK_PASSED = "Safety Check Passed";
    public static final String MSG_PII_TOKENIZED = "PII Tokenization Applied";

    // Common Values
    public static final String VAL_UNSAFE = "UNSAFE";
    public static final String VAL_SAFE = "SAFE";

    private ObservabilityConstants() {
    }
}
