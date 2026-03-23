package com.ultramancode.aiguardrail.experiment.application.usecase.run.support.dataset;

import com.ultramancode.aiguardrail.common.util.DatasetItemSignatureUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DatasetItemIdResolver {

    public String defaultItemId(int itemIndex) {
        return "item-" + itemIndex;
    }

    public String resolveOrDefault(Map<String, Object> item, String defaultValue) {
        String resolved = resolveOrNull(item);
        if (resolved == null || resolved.isBlank()) {
            return defaultValue;
        }
        return resolved;
    }

    public String resolveOrNull(Map<String, Object> item) {
        return DatasetItemSignatureUtils.resolveItemId(item);
    }
}
