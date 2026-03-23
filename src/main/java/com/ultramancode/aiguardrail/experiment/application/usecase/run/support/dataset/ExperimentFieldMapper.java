package com.ultramancode.aiguardrail.experiment.application.usecase.run.support.dataset;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ExperimentFieldMapper {

    public String extractField(Map<String, Object> item, String fieldPath) {
        return resolveField(item, fieldPath);
    }

    public boolean hasField(Map<String, Object> item, String fieldPath) {
        return resolveField(item, fieldPath) != null;
    }

    private String resolveField(Map<String, Object> item, String fieldPath) {
        if (item == null) {
            return null;
        }
        if (fieldPath == null || fieldPath.isBlank()) {
            return null;
        }

        return resolvePath(item, fieldPath);
    }

    private String resolvePath(Map<String, Object> source, String path) {
        Object current = source;

        String[] segments = path.split("\\.");
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                return null;
            }

            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }

        return toSimpleValue(current);
    }

    private String toSimpleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?>) {
            return null;
        }
        return value.toString();
    }
}
