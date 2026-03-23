package com.ultramancode.aiguardrail.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MediaTokenUtils {

    private static final String MEDIA_TOKEN_PREFIX = "@@@langfuseMedia";
    private static final String KEY_STORAGE_KEY = "storageKey";
    private static final String KEY_CONTENT_TYPE = "contentType";
    private static final String META_ATTACHMENT = "attachment";
    private static final String TOKEN_KEY_ID = "id";
    private static final String TOKEN_KEY_TYPE = "type";

    private static final Pattern MEDIA_ID_PATTERN = Pattern.compile("id=([^|@]+)");
    private static final Pattern MEDIA_TYPE_PATTERN = Pattern.compile("type=([^|@]+)");

    /**
     * 메타데이터에서 mediaId/contentType 정보를 함께 추출합니다.
     */
    public static ResolvedMediaInfo resolveMediaInfo(Map<String, Object> metadata) {
        if (metadata == null) {
            log.debug("[MediaTokenUtils] Metadata is null");
            return ResolvedMediaInfo.empty();
        }

        String contentType = asTrimmedString(metadata.get(KEY_CONTENT_TYPE));

        // 1. 직접적인 storageKey 확인
        if (metadata.containsKey(KEY_STORAGE_KEY)) {
            String storageKey = asTrimmedString(metadata.get(KEY_STORAGE_KEY));
            if (storageKey != null) {
                log.debug("[MediaTokenUtils] Found storageKey: {}", storageKey);
                return new ResolvedMediaInfo(storageKey, contentType);
            }
        }

        // 2. Langfuse Media Token 파싱
        // 표준화된 'attachment' 키만 확인
        String token = rawMediaToken(metadata, META_ATTACHMENT);
        if (token == null) {
            return ResolvedMediaInfo.empty();
        }

        ResolvedMediaInfo parsed = parseMediaInfoFromToken(token);
        if (parsed.mediaId() == null || parsed.mediaId().isBlank()) {
            return ResolvedMediaInfo.empty();
        }

        if (parsed.contentType() == null || parsed.contentType().isBlank()) {
            return new ResolvedMediaInfo(parsed.mediaId(), contentType);
        }
        return parsed;
    }

    private static ResolvedMediaInfo parseMediaInfoFromToken(String token) {
        if (token == null) {
            return ResolvedMediaInfo.empty();
        }

        try {
            String mediaId = extractTokenAttribute(token, TOKEN_KEY_ID);
            String contentType = extractTokenAttribute(token, TOKEN_KEY_TYPE);
            if (mediaId == null) {
                return ResolvedMediaInfo.empty();
            }
            log.debug("[MediaTokenUtils] Parsed mediaId: {}, contentType: {}", mediaId, contentType);
            return new ResolvedMediaInfo(mediaId, contentType);
        } catch (RuntimeException e) {
            log.warn("[MediaTokenUtils] Failed to parse mediaId from token: {}", token);
        }
        return ResolvedMediaInfo.empty();
    }

    private static String rawMediaToken(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (!(value instanceof String tokenValue)) {
            return null;
        }
        if (!tokenValue.contains(MEDIA_TOKEN_PREFIX)) {
            return null;
        }
        return tokenValue;
    }

    private static String extractTokenAttribute(String token, String key) {
        Pattern pattern;
        if (TOKEN_KEY_ID.equals(key)) {
            pattern = MEDIA_ID_PATTERN;
        } else if (TOKEN_KEY_TYPE.equals(key)) {
            pattern = MEDIA_TYPE_PATTERN;
        } else {
            return null;
        }

        Matcher matcher = pattern.matcher(token);
        if (matcher.find()) {
            String value = matcher.group(1);
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private static String asTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return text;
    }

    public record ResolvedMediaInfo(String mediaId, String contentType) {
        public static ResolvedMediaInfo empty() {
            return new ResolvedMediaInfo(null, null);
        }
    }
}
