package com.ultramancode.aiguardrail.common.util;

import com.ultramancode.aiguardrail.common.llm.LlmConstants;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.Locale;

/**
 * 멀티모달 입력에서 사용하는 MIME 타입 판별/검증 정책입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MediaTypePolicy {

    public static boolean isPdf(String contentType) {
        String normalized = normalizeContentType(contentType);
        if (normalized == null) {
            return false;
        }

        return MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(normalized);
    }

    public static boolean isImage(String contentType) {
        String normalized = normalizeContentType(contentType);
        if (normalized == null) {
            return false;
        }

        return normalized.toLowerCase(Locale.ROOT).startsWith(LlmConstants.MEDIA_TYPE_IMAGE_PREFIX);
    }

    public static boolean isSupportedMultimodal(String contentType) {
        if (isPdf(contentType)) {
            return true;
        }
        if (isImage(contentType)) {
            return true;
        }
        return false;
    }

    public static String validateImageOrThrow(String contentType) {
        String normalized = normalizeContentType(contentType);
        if (normalized == null) {
            throw new IllegalArgumentException("Unsupported or missing image content type: " + contentType);
        }

        if (!isImage(normalized)) {
            throw new IllegalArgumentException("Unsupported or missing image content type: " + contentType);
        }

        return normalized;
    }

    public static String validatePdfOrThrow(String contentType) {
        String normalized = normalizeContentType(contentType);
        if (normalized == null) {
            throw new IllegalArgumentException("Unsupported or missing PDF content type: " + contentType);
        }

        if (!isPdf(normalized)) {
            throw new IllegalArgumentException("Unsupported or missing PDF content type: " + contentType);
        }

        return normalized;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }

        String trimmed = contentType.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            MimeType parsed = MimeTypeUtils.parseMimeType(trimmed);
            return parsed.getType().toLowerCase(Locale.ROOT)
                    + "/"
                    + parsed.getSubtype().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
