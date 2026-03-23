package com.ultramancode.aiguardrail.guardrail.application.support;

import com.ultramancode.aiguardrail.guardrail.domain.SafetyVerdict;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SafetyVerdictSupport {

    public static void validateOrThrow(SafetyVerdict verdict, String detectorName) {
        String resolvedDetectorName = resolveDetectorName(detectorName);
        if (verdict == null) {
            throw new IllegalStateException(resolvedDetectorName + " returned null verdict");
        }
        if (verdict.verdict() == null) {
            throw new IllegalStateException(resolvedDetectorName + " returned verdict without status");
        }
    }

    private static String resolveDetectorName(String detectorName) {
        if (detectorName == null || detectorName.isBlank()) {
            return "Safety detector";
        }
        return detectorName;
    }
}
