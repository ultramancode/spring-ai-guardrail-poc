package com.ultramancode.aiguardrail.observability.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultramancode.aiguardrail.common.observability.ObservabilityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.ai.chat.metadata.Usage;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langfuse Ingestion Client (High Volume)
 * <p>
 * [책임]
 * - Trace, Generation, Media 등 대량 데이터 전송
 * - Async 처리가 권장되는 Ingestion API 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseIngestionClient {

    @Qualifier("langfuseWebClient")
    private final WebClient client;
    private final ObjectMapper objectMapper;

    /**
     * Vision Direct 로그 (Media 포함) 기록
     */
    public void recordVisionGenerationWithMedia(String traceId, String modelName,
                                                byte[] imageBytes, String contentType,
                                                String question, String response,
                                                long startTime, long endTime,
                                                Usage usage) {
        try {
            // 1. Media Upload
            String mediaId = uploadMedia(traceId, imageBytes, contentType, "input");

            // 2. Prepare Generation
            String generationId = UUID.randomUUID().toString();

            // Media Token - Use source=bytes for better UI compatibility
            String mediaToken = (mediaId != null)
                    ? String.format("@@@langfuseMedia:type=%s|id=%s|source=bytes@@@", contentType, mediaId)
                    : "";

            Map<String, Object> metadata = new HashMap<>();
            if (mediaId != null) {
                metadata.put(ObservabilityConstants.METADATA_ATTACHMENT, mediaToken);
            }

            Map<String, Object> generationBody = new HashMap<>();
            generationBody.put(ObservabilityConstants.LF_ID, generationId);
            generationBody.put(ObservabilityConstants.LF_TRACE_ID, traceId);
            generationBody.put(ObservabilityConstants.LF_NAME, ObservabilityConstants.OP_VISION_DIRECT);
            generationBody.put(ObservabilityConstants.LF_MODEL, modelName);
            generationBody.put(ObservabilityConstants.LF_INPUT, question); // Clean question
            generationBody.put(ObservabilityConstants.LF_OUTPUT, response);
            generationBody.put(ObservabilityConstants.LF_START_TIME, Instant.ofEpochMilli(startTime).toString());
            generationBody.put(ObservabilityConstants.LF_END_TIME, Instant.ofEpochMilli(endTime).toString());
            generationBody.put(ObservabilityConstants.LF_METADATA, metadata);

            if (usage != null) {
                generationBody.put(ObservabilityConstants.LF_USAGE, Map.of(
                        "promptTokens", usage.getPromptTokens(),
                        "completionTokens", usage.getCompletionTokens(),
                        "totalTokens", usage.getTotalTokens()
                ));
            }

            // Send Events - Also keep trace input clean
            sendTraceEvent(traceId, question, response, startTime, metadata);
            sendIngestionEvent(generationBody);

            log.info("[INGESTION] Recorded Vision generation. Tokens: {}",
                    usage != null ? usage.getTotalTokens() : "unknown");

        } catch (Exception e) {
            log.error("[INGESTION] Failed to record vision generation: {}", e.getMessage(), e);
        }
    }

    /**
     * PDF Attachments 등 수동 미디어 기록
     */
    public void recordGenerationWithAttachment(String traceId, String name, String modelName,
                                               String originalFileName, byte[] originalFileBytes,
                                               String question, String extractedText, String llmOutput,
                                               long startTime, long endTime) {
        try {
            // 1. Media Upload (PDF)
            String mediaId = uploadMedia(traceId, originalFileBytes, "application/pdf", "input");

            String mediaToken = (mediaId != null)
                    ? String.format("@@@langfuseMedia:type=application/pdf|id=%s|source=bytes@@@", mediaId)
                    : "[Upload Failed]";

            Map<String, Object> metadata = new HashMap<>();
            metadata.put(ObservabilityConstants.METADATA_ORIGINAL_FILE_NAME, originalFileName);
            if (mediaId != null) {
                metadata.put(ObservabilityConstants.METADATA_ATTACHMENT, mediaToken);
            }
            // Add a snippet of extracted text to metadata instead of input
            metadata.put(ObservabilityConstants.METADATA_EXTRACTED_PREVIEW, extractedText.substring(0, Math.min(extractedText.length(), 1000)));

            String inputContent = "PDF Analysis: " + originalFileName;

            Map<String, Object> generationBody = new HashMap<>();
            generationBody.put(ObservabilityConstants.LF_ID, UUID.randomUUID().toString());
            generationBody.put(ObservabilityConstants.LF_TRACE_ID, traceId);
            generationBody.put(ObservabilityConstants.LF_NAME, name);
            generationBody.put(ObservabilityConstants.LF_MODEL, modelName);
            generationBody.put(ObservabilityConstants.LF_INPUT, inputContent);
            generationBody.put(ObservabilityConstants.LF_OUTPUT, llmOutput);
            generationBody.put(ObservabilityConstants.LF_START_TIME, Instant.ofEpochMilli(startTime).toString());
            generationBody.put(ObservabilityConstants.LF_END_TIME, Instant.ofEpochMilli(endTime).toString());
            generationBody.put(ObservabilityConstants.LF_METADATA, metadata);

            // Fix: Also record trace-create to show Input/Output in UI list
            sendTraceEvent(traceId, question, llmOutput, startTime, metadata);
            sendIngestionEvent(generationBody);

        } catch (Exception e) {
            log.error("[INGESTION] Failed to record attachment: {}", e.getMessage(), e);
        }
    }


    /**
     * 스코어 기록 (Evaluation)
     */
    public void recordScore(String traceId, String name, double value, String comment) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put(ObservabilityConstants.LF_ID, UUID.randomUUID().toString());
            body.put(ObservabilityConstants.LF_TRACE_ID, traceId);
            body.put(ObservabilityConstants.LF_NAME, name);
            body.put(ObservabilityConstants.LF_VALUE, value);
            if (comment != null) {
                body.put(ObservabilityConstants.LF_COMMENT, comment);
            }

            Map<String, Object> event = Map.of(
                    ObservabilityConstants.LF_ID, UUID.randomUUID().toString(),
                    ObservabilityConstants.LF_TYPE, "score-create",
                    ObservabilityConstants.LF_TIMESTAMP, Instant.now().toString(),
                    ObservabilityConstants.LF_BODY, body
            );
            sendBatch(event);

            log.info("[INGESTION] Recorded Score: {} = {}", name, value);
        } catch (Exception e) {
            log.error("[INGESTION] Failed to record score: {}", e.getMessage(), e);
        }
    }

    /**
     * Langfuse Media 다운로드 (Pre-signed URL 방식)
     */
    public byte[] downloadMedia(String mediaId) {
        try {
            log.info("[INGESTION] Fetching media metadata from Langfuse: {}", mediaId);

            // 1. Get Media Metadata (includes pre-signed URL)
            String responseStr = client.get()
                    .uri("/api/public/media/" + mediaId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode mediaNode = objectMapper.readTree(responseStr);
            String downloadUrl = mediaNode.get("url").asText();

            if (downloadUrl == null || downloadUrl.isEmpty()) {
                log.error("[INGESTION] No download URL found for media: {}", mediaId);
                return null;
            }

            // 2. Download from Pre-signed URL
            okhttp3.OkHttpClient okHttpClient = new okhttp3.OkHttpClient();
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(downloadUrl)
                    .get()
                    .build();

            try (okhttp3.Response downloadResponse = okHttpClient.newCall(request).execute()) {
                if (!downloadResponse.isSuccessful()) {
                    throw new RuntimeException("Download failed: " + downloadResponse.code());
                }
                return downloadResponse.body().bytes();
            }

        } catch (Exception e) {
            log.error("[INGESTION] Failed to download media {}: {}", mediaId, e.getMessage());
            return null;
        }
    }

    // Internal Helpers

    public String uploadMedia(String traceId, byte[] content, String contentType, String field) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String sha256Hash = Base64.getEncoder().encodeToString(digest.digest(content));

            // 1. Get Init URL
            Map<String, Object> initBody = Map.of(
                    ObservabilityConstants.LF_TRACE_ID, traceId,
                    ObservabilityConstants.LF_CONTENT_TYPE, contentType,
                    "contentLength", content.length,
                    "sha256Hash", sha256Hash,
                    "field", field
            );

            String initResponse = client.post()
                    .uri("/api/public/media")
                    .bodyValue(initBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode initNode = objectMapper.readTree(initResponse);
            String mediaId = initNode.get("mediaId").asText();
            JsonNode uploadUrlNode = initNode.get("uploadUrl");

            if (uploadUrlNode == null || uploadUrlNode.isNull()) return mediaId;

            // 2. Upload to S3/MinIO (Direct PUT) - using OkHttp for raw body support
            String uploadUrl = uploadUrlNode.asText();
            okhttp3.OkHttpClient okHttpClient = new okhttp3.OkHttpClient();
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(uploadUrl)
                    .put(okhttp3.RequestBody.create(content, okhttp3.MediaType.parse(contentType)))
                    .header("x-amz-checksum-sha256", sha256Hash)
                    .build();

            try (okhttp3.Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new RuntimeException("Upload failed: " + response.code());
            }

            // 3. Patch Status
            client.patch()
                    .uri("/api/public/media/" + mediaId)
                    .bodyValue(Map.of("uploadedAt", Instant.now().toString(), "uploadHttpStatus", 200))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            return mediaId;

        } catch (Exception e) {
            log.error("[INGESTION] Media upload failed: {}", e.getMessage());
            return null;
        }
    }

    private void sendIngestionEvent(Map<String, Object> body) {
        Map<String, Object> event = Map.of(
                ObservabilityConstants.LF_ID, UUID.randomUUID().toString(),
                ObservabilityConstants.LF_TYPE, "generation-create",
                ObservabilityConstants.LF_TIMESTAMP, Instant.now().toString(),
                ObservabilityConstants.LF_BODY, body
        );
        sendBatch(event);
    }

    private void sendTraceEvent(String traceId, String input, String output, long startTime, Map<String, Object> metadata) {
        Map<String, Object> body = new HashMap<>();
        body.put(ObservabilityConstants.LF_ID, traceId);
        body.put(ObservabilityConstants.LF_NAME, ObservabilityConstants.OP_VISION_CONVERSATION);
        body.put(ObservabilityConstants.LF_TIMESTAMP, Instant.ofEpochMilli(startTime).toString());
        body.put(ObservabilityConstants.LF_INPUT, input);
        body.put(ObservabilityConstants.LF_OUTPUT, output);
        if (metadata != null) {
            body.put(ObservabilityConstants.LF_METADATA, metadata);
        }

        Map<String, Object> event = Map.of(
                ObservabilityConstants.LF_ID, UUID.randomUUID().toString(),
                ObservabilityConstants.LF_TYPE, "trace-create",
                ObservabilityConstants.LF_TIMESTAMP, Instant.now().toString(),
                ObservabilityConstants.LF_BODY, body
        );
        sendBatch(event);
    }

    private void sendBatch(Map<String, Object> event) {
        Map<String, Object> payload = Map.of(
                ObservabilityConstants.LF_BATCH, List.of(event),
                ObservabilityConstants.LF_METADATA, Map.of("sdk_name", ObservabilityConstants.VAL_SDK_NAME, "sdk_version", ObservabilityConstants.VAL_SDK_VERSION)
        );
        client.post()
                .uri("/api/public/ingestion")
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}

