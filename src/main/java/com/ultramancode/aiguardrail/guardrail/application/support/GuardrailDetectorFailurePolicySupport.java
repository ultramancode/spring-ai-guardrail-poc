package com.ultramancode.aiguardrail.guardrail.application.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;

import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GuardrailDetectorFailurePolicySupport {

    private static final String POLICY_ALLOW = "allow";
    private static final String POLICY_BLOCK = "block";
    private static final String POLICY_TRUE = "true";
    private static final String POLICY_FALSE = "false";

    public static boolean shouldAllowOnDetectorFailure(String policy, String logPrefix, Logger log) {
        if (policy == null) {
            return false;
        }

        String normalizedPolicy = policy.trim().toLowerCase(Locale.ROOT);
        if (normalizedPolicy.isEmpty()) {
            return false;
        }
        if (POLICY_ALLOW.equals(normalizedPolicy) || POLICY_TRUE.equals(normalizedPolicy)) {
            return true;
        }
        if (POLICY_BLOCK.equals(normalizedPolicy) || POLICY_FALSE.equals(normalizedPolicy)) {
            return false;
        }

        log.warn("{} Invalid on-detector-error policy: {}. Fallback to block.", logPrefix, policy);
        return false;
    }
}
