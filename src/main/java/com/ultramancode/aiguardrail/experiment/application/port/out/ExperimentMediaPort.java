package com.ultramancode.aiguardrail.experiment.application.port.out;

import java.util.Map;

public interface ExperimentMediaPort {

    byte[] downloadMedia(String mediaId);

    ResolvedMediaInfo resolveMediaInfo(Map<String, Object> metadata);

    record ResolvedMediaInfo(String mediaId, String contentType) {
        public static ResolvedMediaInfo empty() {
            return new ResolvedMediaInfo(null, null);
        }
    }
}
