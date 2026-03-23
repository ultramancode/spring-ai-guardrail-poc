package com.ultramancode.aiguardrail.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageSignatureUtils {

    public static String buildPageSignature(
            List<Map<String, Object>> pageItems,
            Function<Map<String, Object>, String> itemSignatureResolver
    ) {
        if (pageItems == null || pageItems.isEmpty()) {
            return null;
        }
        if (itemSignatureResolver == null) {
            return null;
        }

        StringBuilder signatureBuilder = new StringBuilder();
        signatureBuilder.append(pageItems.size());

        for (Map<String, Object> pageItem : pageItems) {
            String itemSignature = itemSignatureResolver.apply(pageItem);
            if (itemSignature == null || itemSignature.isBlank()) {
                return null;
            }
            signatureBuilder
                    .append("|")
                    .append(itemSignature.length())
                    .append(":")
                    .append(itemSignature);
        }

        return signatureBuilder.toString();
    }
}
