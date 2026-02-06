package com.ultramancode.aiguardrail.common.observability;

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
    public static final String TAG_LATENCY_MS = "latency.ms";


    // Standard KV Tags for Observations (Micrometer)
    public static final String TAG_INPUT = "input";
    public static final String TAG_OUTPUT = "output";

    // Langfuse Observation Types
    public static final String LF_OBSERVATION_TYPE = "langfuse.observation.type";
    public static final String LF_VAL_GUARDRAIL = "guardrail";
    public static final String LF_VAL_EVALUATOR = "evaluator";
    public static final String LF_VAL_CHAIN = "chain";
    public static final String LF_VAL_TOOL = "tool";

    // Langfuse Prompt Linking
    public static final String LF_PROMPT_NAME = "langfuse.prompt.name";
    public static final String LF_PROMPT_VERSION = "langfuse.prompt.version";

    // GenAI Semantic Conventions (OTel)
    public static final String TAG_GEN_AI_PROMPT = "gen_ai.prompt";
    public static final String TAG_GEN_AI_COMPLETION = "gen_ai.completion";
    public static final String TAG_INPUT_VALUE = "input.value";
    public static final String TAG_OUTPUT_VALUE = "output.value";

    // Metadata Keys for Langfuse
    public static final String METADATA_ATTACHMENT = "attachment";
    public static final String METADATA_EXTRACTED_PREVIEW = "extracted_preview";
    public static final String METADATA_ORIGINAL_FILE_NAME = "original_file_name";


    // Log Messages & Comments
    public static final String MSG_SAFETY_CHECK_PASSED = "Safety Check Passed";
    public static final String MSG_PII_TOKENIZED = "PII Tokenization Applied";

    // Common Values
    public static final String VAL_UNSAFE = "UNSAFE";
    public static final String VAL_SAFE = "SAFE";
    public static final String VAL_SDK_NAME = "spring-ai-guardrail-poc";
    public static final String VAL_SDK_VERSION = "1.0.0";

    // Placeholders
    public static final String MASKED_INPUT_PLACEHOLDER = "[MASKED_INPUT]";

    // Langfuse Ingestion API Keys (Internal Protocol)
    public static final String LF_ID = "id";
    public static final String LF_TRACE_ID = "traceId";
    public static final String LF_PARENT_OBSERVATION_ID = "parentObservationId";
    public static final String LF_NAME = "name";
    public static final String LF_MODEL = "model";
    public static final String LF_INPUT = "input";
    public static final String LF_OUTPUT = "output";

    // Managed Prompt Names
    // Removed specific prompt constants in favor of application.properties defaults

    public static final String LF_START_TIME = "startTime";
    public static final String LF_END_TIME = "endTime";
    public static final String LF_USAGE = "usage";
    public static final String LF_METADATA = "metadata";
    public static final String LF_TIMESTAMP = "timestamp";
    public static final String LF_TYPE = "type";
    public static final String LF_BODY = "body";
    public static final String LF_BATCH = "batch";
    public static final String LF_VALUE = "value";
    public static final String LF_COMMENT = "comment";
    public static final String LF_RUN_NAME = "runName";
    public static final String LF_DATASET_ITEM_ID = "datasetItemId";
    public static final String LF_LEVEL = "level";

    public static final String LF_STATUS_MESSAGE = "statusMessage";
    public static final String LF_CONTENT_TYPE = "contentType";


    // Operation Names
    public static final String OP_VISION_DIRECT = "vision-direct-media";
    public static final String OP_VISION_CONVERSATION = "vision-conversation-turn";


    private ObservabilityConstants() {
    }
}
