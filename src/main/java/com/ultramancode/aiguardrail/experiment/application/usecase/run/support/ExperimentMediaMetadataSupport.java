package com.ultramancode.aiguardrail.experiment.application.usecase.run.support;

import com.ultramancode.aiguardrail.common.util.StringValueUtils;
import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentMediaPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 메타데이터에서 미디어 식별자를 해석한다.
 */
@Component
@RequiredArgsConstructor
public class ExperimentMediaMetadataSupport {

    private final ExperimentMediaPort mediaPort;

    public String extractMediaId(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }

        ExperimentMediaPort.ResolvedMediaInfo mediaInfo = mediaPort.resolveMediaInfo(metadata);
        return StringValueUtils.asNonBlankString(mediaInfo.mediaId());
    }
}
