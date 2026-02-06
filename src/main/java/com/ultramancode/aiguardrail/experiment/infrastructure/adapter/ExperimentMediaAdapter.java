package com.ultramancode.aiguardrail.experiment.infrastructure.adapter;

import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentMediaPort;
import com.ultramancode.aiguardrail.observability.infrastructure.client.LangfuseIngestionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentMediaAdapter implements ExperimentMediaPort {

    private final LangfuseIngestionClient langfuseClient;

    @Override
    public byte[] downloadMedia(String mediaId) {
        return langfuseClient.downloadMedia(mediaId);
    }
}
