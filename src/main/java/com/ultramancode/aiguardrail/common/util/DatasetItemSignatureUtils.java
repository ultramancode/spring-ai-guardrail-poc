package com.ultramancode.aiguardrail.common.util;

import com.ultramancode.aiguardrail.common.observability.LangfuseConstants;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DatasetItemSignatureUtils {

    private static final List<String> ITEM_ID_KEYS = List.of("id", "datasetItemId", "dataset_item_id");

    public static String resolveSignature(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return null;
        }

        String resolvedId = resolveItemId(item);
        if (resolvedId != null && !resolvedId.isBlank()) {
            return "id:" + resolvedId;
        }

        String traceIdValue = StringValueUtils.asNonBlankString(item.get(LangfuseConstants.KEY_TRACE_ID));
        String runNameValue = StringValueUtils.asNonBlankString(item.get(LangfuseConstants.KEY_RUN_NAME));
        if (traceIdValue != null || runNameValue != null) {
            String safeTraceIdValue = traceIdValue;
            if (safeTraceIdValue == null) {
                safeTraceIdValue = "";
            }

            String safeRunNameValue = runNameValue;
            if (safeRunNameValue == null) {
                safeRunNameValue = "";
            }

            return "trace:" + safeTraceIdValue + "|run:" + safeRunNameValue;
        }

        String stableHash = StableMapSignatureUtils.buildStableHash(item);
        if (stableHash == null || stableHash.isBlank()) {
            return null;
        }

        return "hash:" + stableHash;
    }

    public static String resolveItemId(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return null;
        }

        String resolvedFromRoot = resolveItemIdByKeys(item);
        if (resolvedFromRoot != null) {
            return resolvedFromRoot;
        }

        Object metadataValue = item.get(LangfuseConstants.KEY_METADATA);
        if (metadataValue instanceof Map<?, ?> metadataMap) {
            Map<String, Object> normalizedMetadata = new LinkedHashMap<>();
            metadataMap.forEach((metadataKey, metadataEntryValue) -> {
                if (metadataKey != null) {
                    normalizedMetadata.put(String.valueOf(metadataKey), metadataEntryValue);
                }
            });
            return resolveItemIdByKeys(normalizedMetadata);
        }

        return null;
    }

    private static String resolveItemIdByKeys(Map<String, Object> source) {
        for (String key : ITEM_ID_KEYS) {
            String resolvedValue = StringValueUtils.asNonBlankString(source.get(key));
            if (resolvedValue != null) {
                return resolvedValue;
            }
        }
        return null;
    }
}
