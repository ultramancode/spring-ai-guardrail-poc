package com.ultramancode.aiguardrail.common.observability;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Langfuse 연동 시 사용하는 공통 상수 모음입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LangfuseConstants {

    public static final String KEY_TRACE_ID = "traceId";
    public static final String KEY_ID = "id";
    public static final String KEY_NAME = "name";
    public static final String KEY_METADATA = ObservabilityTags.KEY_METADATA;
    public static final String KEY_INPUT = ObservabilityTags.KEY_INPUT;
    public static final String KEY_OUTPUT = ObservabilityTags.KEY_OUTPUT;
    public static final String KEY_VALUE = "value";
    public static final String KEY_COMMENT = "comment";
    public static final String KEY_CREATED_AT = "createdAt";
    public static final String KEY_UPDATED_AT = "updatedAt";
    public static final String KEY_RUN_NAME = "runName";
    public static final String KEY_DATASET_ITEM_ID = "datasetItemId";
    public static final String KEY_CONTENT_TYPE = "contentType";

    // Observation 타입 태그
    public static final String TAG_OBSERVATION_TYPE = "langfuse.observation.type";
}
