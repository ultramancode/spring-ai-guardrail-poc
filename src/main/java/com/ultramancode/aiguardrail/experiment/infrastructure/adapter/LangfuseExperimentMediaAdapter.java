package com.ultramancode.aiguardrail.experiment.infrastructure.adapter;

import com.ultramancode.aiguardrail.common.integration.langfuse.client.LangfuseIngestionClient;
import com.ultramancode.aiguardrail.common.util.MediaTokenUtils;
import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentMediaPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class LangfuseExperimentMediaAdapter implements ExperimentMediaPort {

    private final LangfuseIngestionClient langfuseClient;

    @Override
    public byte[] downloadMedia(String mediaId) {
        return langfuseClient.downloadMedia(mediaId);
    }

    @Override
    public ResolvedMediaInfo resolveMediaInfo(Map<String, Object> metadata) {
        MediaTokenUtils.ResolvedMediaInfo resolvedMediaInfo = MediaTokenUtils.resolveMediaInfo(metadata);
        return new ResolvedMediaInfo(resolvedMediaInfo.mediaId(), resolvedMediaInfo.contentType());
    }
}
