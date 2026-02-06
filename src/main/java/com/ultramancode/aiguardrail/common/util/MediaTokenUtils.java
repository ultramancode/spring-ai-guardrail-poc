package com.ultramancode.aiguardrail.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class MediaTokenUtils {

    private static final Pattern MEDIA_ID_PATTERN = Pattern.compile("id=([^|@]+)");

    /**
     * 메타데이터에서 mediaId를 추출합니다.
     * 1. 'storageKey' 확인
     * 2. 'attachment' 등에서 Langfuse Media Token 파싱
     */
    public static String resolveMediaId(Map<String, Object> metadata) {
        if (metadata == null) return null;

        // 1. 직접적인 storageKey 확인
        if (metadata.containsKey("storageKey")) {
            return (String) metadata.get("storageKey");
        }

        // 2. Langfuse Media Token 파싱
        // 표준화된 'attachment' 키만 확인
        return extractMediaToken(metadata, "attachment");
    }

    /**
     * 특정 키(예: attachment)에서만 토큰을 찾고 싶을 때 사용
     */
    public static String extractMediaToken(Map<String, Object> metadata, String key) {
        if (metadata == null) return null;
        Object val = metadata.get(key);
        if (val instanceof String && ((String) val).contains("@@@langfuseMedia")) {
            return parseMediaIdFromToken((String) val);
        }
        return null;
    }

    public static String parseMediaIdFromToken(String token) {
        if (token == null) return null;

        // Format: @@@langfuseMedia:type=...|id=xxx|source=...@@@
        try {
            Matcher matcher = MEDIA_ID_PATTERN.matcher(token);
            if (matcher.find()) {
                String id = matcher.group(1);
                log.debug("[MediaTokenUtils] Parsed mediaId: {}", id);
                return id;
            }
        } catch (Exception e) {
            log.warn("[MediaTokenUtils] Failed to parse mediaId from token: {}", token);
        }
        return null;
    }
}
