package com.ultramancode.aiguardrail.common.integration.langfuse.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultramancode.aiguardrail.common.integration.langfuse.client.dto.LangfuseIngestionDto.MediaInitRequest;
import com.ultramancode.aiguardrail.common.integration.langfuse.client.dto.LangfuseIngestionDto.MediaStatusUpdate;
import com.ultramancode.aiguardrail.common.observability.TraceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * Langfuse 미디어 업로드/다운로드 전용 클라이언트입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseMediaClient {

    private static final String DEFAULT_UPLOAD_FIELD = "input";
    private static final String DEFAULT_UPLOAD_CONTENT_TYPE = "application/octet-stream";

    @Qualifier("langfuseWebClient")
    private final WebClient client;
    @Qualifier("langfuseMediaHttpClient")
    private final okhttp3.OkHttpClient mediaHttpClient;
    private final ObjectMapper objectMapper;

    @Value("${langfuse.media.fail-open:false}")
    private boolean mediaFailOpen;

    public byte[] downloadMedia(String mediaId) {
        try {
            if (mediaId == null || mediaId.isBlank()) {
                throw new IllegalArgumentException("mediaId must not be blank");
            }

            log.info("[INGESTION] Fetching media metadata: {}", mediaId);

            String responseStr = client.get()
                    .uri("/api/public/media/" + mediaId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (responseStr == null || responseStr.isBlank()) {
                throw new IllegalStateException("Media metadata response is empty");
            }

            JsonNode mediaNode = objectMapper.readTree(responseStr);
            String downloadUrl = mediaNode.path("url").asText("");
            if (downloadUrl.isEmpty()) {
                return handleMediaFailure(
                        "download media " + mediaId,
                        new IllegalStateException("No download URL found for mediaId: " + mediaId),
                        null
                );
            }

            okhttp3.Request request = new okhttp3.Request.Builder().url(downloadUrl).get().build();
            try (okhttp3.Response downloadResponse = mediaHttpClient.newCall(request).execute()) {
                if (!downloadResponse.isSuccessful()) {
                    throw new IllegalStateException("Download failed: " + downloadResponse.code());
                }

                okhttp3.ResponseBody responseBody = downloadResponse.body();
                if (responseBody == null) {
                    throw new IllegalStateException("Download response body is empty");
                }

                return responseBody.bytes();
            }
        } catch (IOException e) {
            return handleMediaFailure("download media " + mediaId, e, null);
        } catch (RuntimeException e) {
            return handleMediaFailure("download media " + mediaId, e, null);
        }
    }

    public String uploadMedia(String traceId, byte[] content, String contentType, String field) {
        try {
            if (!TraceUtils.isValid(traceId)) {
                throw new IllegalArgumentException("traceId must not be blank");
            }
            if (content == null || content.length == 0) {
                throw new IllegalArgumentException("content must not be null or empty");
            }

            String resolvedContentType = resolveUploadContentType(contentType);
            String resolvedField = resolveUploadField(field);
            String sha256Hash = encodeSha256(content);

            MediaInitRequest initRequest = new MediaInitRequest(
                    traceId,
                    resolvedContentType,
                    content.length,
                    sha256Hash,
                    resolvedField
            );

            String initResponse = client.post()
                    .uri("/api/public/media")
                    .bodyValue(initRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (initResponse == null || initResponse.isBlank()) {
                throw new IllegalStateException("Media init response is empty");
            }

            JsonNode initNode = objectMapper.readTree(initResponse);
            JsonNode mediaIdNode = initNode.get("mediaId");
            if (mediaIdNode == null || mediaIdNode.isNull() || mediaIdNode.asText("").isBlank()) {
                throw new IllegalStateException("mediaId is missing in media init response");
            }

            String mediaId = mediaIdNode.asText();
            JsonNode uploadUrlNode = initNode.get("uploadUrl");
            if (uploadUrlNode == null || uploadUrlNode.isNull()) {
                return mediaId;
            }

            String uploadUrl = uploadUrlNode.asText();
            if (uploadUrl.isBlank()) {
                throw new IllegalStateException("uploadUrl is blank in media init response");
            }

            uploadToStorage(uploadUrl, content, resolvedContentType, sha256Hash);
            updateUploadStatus(mediaId);
            return mediaId;
        } catch (IOException | NoSuchAlgorithmException e) {
            return handleMediaFailure("upload media", e, null);
        } catch (RuntimeException e) {
            return handleMediaFailure("upload media", e, null);
        }
    }

    private String encodeSha256(byte[] content) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Base64.getEncoder().encodeToString(digest.digest(content));
    }

    private void uploadToStorage(String uploadUrl, byte[] content, String contentType, String sha256Hash)
            throws IOException {
        okhttp3.MediaType parsedMediaType = okhttp3.MediaType.parse(contentType);
        if (parsedMediaType == null) {
            throw new IllegalArgumentException("Invalid contentType: " + contentType);
        }

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(uploadUrl)
                .put(okhttp3.RequestBody.create(content, parsedMediaType))
                .header("x-amz-checksum-sha256", sha256Hash)
                .build();

        try (okhttp3.Response response = mediaHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Upload failed: " + response.code());
            }
        }
    }

    private void updateUploadStatus(String mediaId) {
        MediaStatusUpdate statusUpdate = new MediaStatusUpdate(Instant.now().toString(), 200);
        client.patch()
                .uri("/api/public/media/" + mediaId)
                .bodyValue(statusUpdate)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private String resolveUploadContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_UPLOAD_CONTENT_TYPE;
        }

        return contentType;
    }

    private String resolveUploadField(String field) {
        if (field == null || field.isBlank()) {
            return DEFAULT_UPLOAD_FIELD;
        }

        return field;
    }

    private <T> T handleMediaFailure(String actionName, Throwable e, T fallbackValue) {
        log.error("[INGESTION] Failed to {}: {}", actionName, e.getMessage(), e);
        if (mediaFailOpen) {
            return fallbackValue;
        }

        throw new IllegalStateException("Failed to " + actionName, e);
    }
}
