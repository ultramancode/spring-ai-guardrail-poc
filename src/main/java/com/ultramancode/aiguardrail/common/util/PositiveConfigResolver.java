package com.ultramancode.aiguardrail.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PositiveConfigResolver {

    public static int resolve(int configured, int fallback, String logPrefix, String propertyName) {
        if (configured > 0) {
            return configured;
        }

        log.warn(
                "{} Invalid {}={} detected. Using fallback={}.",
                logPrefix,
                propertyName,
                configured,
                fallback
        );
        return fallback;
    }
}
