package com.ultramancode.aiguardrail.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.function.Consumer;

/**
 * 멀티모달 입력(text/file) 검증과 본문 길이 제한 로직을 공통으로 제공합니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MultimodalValidationUtils {

    public static String normalizeAndValidateText(String text, String fieldName, int maxTextLength) {
        String normalized = text;
        if (normalized == null) {
            normalized = "";
        } else {
            normalized = normalized.trim();
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        if (maxTextLength > 0 && normalized.length() > maxTextLength) {
            throw new IllegalArgumentException(fieldName + " exceeds max length. max=" + maxTextLength);
        }

        return normalized;
    }

    public static void validateMultipartFile(MultipartFile file, long maxFileSizeBytes, String fieldName) {
        if (file == null) {
            throw new IllegalArgumentException(fieldName + " file is required.");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " file is empty.");
        }

        validateFileSize(file.getSize(), file.getOriginalFilename(), maxFileSizeBytes, fieldName);
    }

    public static void validateFileSize(long fileSize, String fileName, long maxFileSizeBytes, String fileTypeLabel) {
        if (maxFileSizeBytes <= 0) {
            return;
        }
        if (fileSize <= maxFileSizeBytes) {
            return;
        }

        throw new IllegalArgumentException(
                fileTypeLabel + " file size exceeds limit. file=" + fileName
                        + ", size=" + fileSize
                        + ", max=" + maxFileSizeBytes
        );
    }

    public static String truncateText(
            String text,
            int maxChars,
            String contentLabel,
            Consumer<String> warnLogger
    ) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0) {
            return text;
        }
        if (text.length() <= maxChars) {
            return text;
        }

        if (warnLogger != null) {
            warnLogger.accept(
                    contentLabel + " 길이 초과로 잘라냅니다. from="
                            + text.length()
                            + ", to="
                            + maxChars
            );
        }
        return text.substring(0, maxChars);
    }
}
