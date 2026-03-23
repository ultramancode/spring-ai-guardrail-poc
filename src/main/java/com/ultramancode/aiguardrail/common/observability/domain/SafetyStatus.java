package com.ultramancode.aiguardrail.common.observability.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 안전성 상태
 */
@Getter
@RequiredArgsConstructor
public enum SafetyStatus {
    SAFE("SAFE"),
    UNSAFE("UNSAFE");

    private final String value;
}
