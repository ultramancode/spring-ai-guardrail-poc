package com.ultramancode.aiguardrail.common.observability;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 공통 Observability 태그 및 키 정의 클래스
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ObservabilityTags {

    // 공통 Map 키
    public static final String KEY_INPUT = "input";
    public static final String KEY_OUTPUT = "output";
    public static final String KEY_METADATA = "metadata";

}
