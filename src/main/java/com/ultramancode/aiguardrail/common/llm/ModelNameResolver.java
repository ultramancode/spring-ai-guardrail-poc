package com.ultramancode.aiguardrail.common.llm;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ModelNameResolver {

    public static String resolve(String vendor, String model) {
        String resolvedVendor;
        if (vendor == null || vendor.isBlank()) {
            resolvedVendor = "default";
        } else {
            resolvedVendor = vendor.trim();
        }

        String resolvedModel;
        if (model == null || model.isBlank()) {
            resolvedModel = "default";
        } else {
            resolvedModel = model.trim();
        }

        return resolvedVendor + "/" + resolvedModel;
    }
}
