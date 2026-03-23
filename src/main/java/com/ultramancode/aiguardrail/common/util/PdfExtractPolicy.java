package com.ultramancode.aiguardrail.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Consumer;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PdfExtractPolicy {

    public static final String MODE_FAIL = "fail";
    public static final String MODE_LLM_NOTICE = "llm_notice";
    public static final String EXTRACTION_FAIL_NOTICE =
            "[PDF extraction failed. The document text could not be parsed. " +
                    "Please answer using only the user question.]";

    private static boolean isFailMode(String policy) {
        if (policy == null) {
            return false;
        }
        return MODE_FAIL.equalsIgnoreCase(policy);
    }

    private static boolean isKnownPolicy(String policy) {
        if (policy == null) {
            return false;
        }
        if (MODE_FAIL.equalsIgnoreCase(policy)) {
            return true;
        }
        if (MODE_LLM_NOTICE.equalsIgnoreCase(policy)) {
            return true;
        }
        return false;
    }

    public static String extractTextOrNotice(
            String policy,
            ThrowingTextExtractor extractor,
            Consumer<String> warnLogger
    ) {
        try {
            return extractor.extract();
        } catch (RuntimeException e) {
            if (isFailMode(policy)) {
                throw new IllegalStateException("PDF text extraction failed", e);
            }

            if (!isKnownPolicy(policy) && warnLogger != null) {
                warnLogger.accept("Invalid guardrail.pdf.on-extract-fail policy: " + policy + ". Fallback to llm_notice.");
            }
            if (warnLogger != null) {
                warnLogger.accept("PDF extraction failed. Proceeding with notice context. cause=" + e.getMessage());
            }
            return EXTRACTION_FAIL_NOTICE;
        }
    }

    @FunctionalInterface
    public interface ThrowingTextExtractor {
        String extract();
    }
}
