package com.ultramancode.aiguardrail.experiment.application.usecase.run.support;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import com.ultramancode.aiguardrail.common.observability.LangfuseConstants;
import com.ultramancode.aiguardrail.common.util.StringValueUtils;
import com.ultramancode.aiguardrail.experiment.application.port.out.ExperimentMediaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 실험 실행에 필요한 미디어 파일을 로딩한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperimentFileSupport {

    private static final String KEY_ORIGINAL_NAME = "originalName";
    private static final String KEY_ORIGINAL_FILE_NAME = "original_file_name";

    private final ExperimentMediaPort mediaPort;

    @Value("${experiment.media.download.fail-open:false}")
    private boolean mediaDownloadFailOpen;

    /**
     * metadata에서 mediaId를 읽어 파일을 조회한다.
     */
    public AttachmentFile loadFile(Map<String, Object> metadata) {
        if (metadata == null) {
            log.warn("[EXPERIMENT] Metadata is null. Skip media loading.");
            return null;
        }

        ExperimentMediaPort.ResolvedMediaInfo mediaInfo = mediaPort.resolveMediaInfo(metadata);
        String mediaId = StringValueUtils.asNonBlankString(mediaInfo.mediaId());

        if (mediaId == null) {
            log.warn("[EXPERIMENT] No mediaId found in metadata. Keys found: {}", metadata.keySet());
            return null;
        }

        log.info("[EXPERIMENT] Downloading media via Port: {}", mediaId);

        try {
            byte[] content = mediaPort.downloadMedia(mediaId);
            if (content == null) {
                log.warn("[EXPERIMENT] Media download returned null content. mediaId={}", mediaId);
                return null;
            }

            String filename = resolveFileName(metadata, mediaId);
            String contentType = resolveContentType(metadata, mediaInfo, filename);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return new AttachmentFile(filename, contentType, content);
        } catch (RuntimeException e) {
            if (!mediaDownloadFailOpen) {
                throw new IllegalStateException("Failed to fetch media via Port: " + mediaId, e);
            }
            log.error("[EXPERIMENT] Failed to fetch media via Port (fail-open): {}", mediaId, e);
            return null;
        }
    }

    private String resolveContentType(
            Map<String, Object> metadata,
            ExperimentMediaPort.ResolvedMediaInfo mediaInfo,
            String fileName
    ) {
        Object rawContentType = metadata.get(LangfuseConstants.KEY_CONTENT_TYPE);
        String metadataContentType = StringValueUtils.asNonBlankString(rawContentType);
        if (metadataContentType != null) {
            return metadataContentType;
        }

        String tokenContentType = StringValueUtils.asNonBlankString(mediaInfo.contentType());
        if (tokenContentType != null) {
            return tokenContentType;
        }

        String inferredContentType = inferContentType(fileName);
        if (inferredContentType != null) {
            return inferredContentType;
        }

        return null;
    }

    private String resolveFileName(Map<String, Object> metadata, String mediaId) {
        String originalName = StringValueUtils.asNonBlankString(metadata.get(KEY_ORIGINAL_NAME));
        if (originalName != null) {
            return originalName;
        }

        String originalFileName = StringValueUtils.asNonBlankString(metadata.get(KEY_ORIGINAL_FILE_NAME));
        if (originalFileName != null) {
            return originalFileName;
        }

        return mediaId;
    }

    private String inferContentType(String fileName) {
        if (fileName == null) {
            return null;
        }

        if (fileName.isBlank()) {
            return null;
        }

        return MediaTypeFactory.getMediaType(fileName)
                .map(MediaType::toString)
                .orElse(null);
    }
}
