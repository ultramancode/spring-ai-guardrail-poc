package com.ultramancode.aiguardrail.experiment.application.service;

import com.ultramancode.aiguardrail.common.util.MediaTokenUtils;
import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentMediaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Experiment File Loader (Langfuse API Version)
 * Fetches files using Langfuse's pre-signed URL mechanism.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperimentFileLoader {

    private final ExperimentMediaPort mediaPort;

    /**
     * 메타데이터에서 mediaId를 추출하여 파일을 로드
     * - storageKey가 직접 있으면 사용
     * - 없으면 attachment, document, image 등에서 @@@langfuseMedia:id=...@@@ 토큰 파싱
     */
    public MultipartFile loadFile(Map<String, Object> metadata) {
        String mediaId = resolveMediaId(metadata);
        String contentType = (String) metadata.get("contentType");

        if (mediaId == null || mediaId.isEmpty()) {
            log.warn("[EXPERIMENT] No mediaId found in metadata. Keys found: {}", metadata.keySet());
            return null;
        }

        log.info("[EXPERIMENT] Downloading media via Port: {}", mediaId);

        try {
            byte[] content = mediaPort.downloadMedia(mediaId);
            if (content == null) return null;

            // Determine filename (use mediaId if originalName not available)
            String filename = (String) metadata.getOrDefault("originalName", mediaId);

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return new ExperimentMultipartFile("file", filename, contentType, content);

        } catch (Exception e) {
            log.error("[EXPERIMENT] Failed to fetch media via Port: {}", mediaId, e);
            return null;
        }
    }

    private String resolveMediaId(Map<String, Object> metadata) {
        return MediaTokenUtils.resolveMediaId(metadata);
    }
}
