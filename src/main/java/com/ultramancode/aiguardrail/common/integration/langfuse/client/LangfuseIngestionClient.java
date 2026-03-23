package com.ultramancode.aiguardrail.common.integration.langfuse.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultramancode.aiguardrail.common.integration.langfuse.client.dto.LangfuseIngestionDto.*;
import com.ultramancode.aiguardrail.common.llm.LlmConstants;
import com.ultramancode.aiguardrail.common.observability.TraceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * Langfuse Ingestion 클라이언트 (대량 데이터 전송 전용)
 * [책임]
 * - Trace, Generation, Score 이벤트 전송
 * - 미디어 업로드/다운로드는 LangfuseMediaClient에 위임
 * - 비동기 처리가 권장되는 Ingestion API 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseIngestionClient {

    // 미디어 토큰 포맷
    private static final String MEDIA_TOKEN_FORMAT = "@@@langfuseMedia:type=%s|id=%s|source=bytes@@@";
    private static final String MEDIA_UPLOAD_FAILED = "[Upload Failed]";
    private static final String DEFAULT_UPLOAD_CONTENT_TYPE = "application/octet-stream";
    private static final String ATTACHMENT_LABEL_PDF = "PDF analysis";
    private static final String ATTACHMENT_LABEL_IMAGE = "Image analysis";
    private static final String ATTACHMENT_LABEL_TEXT = "Text analysis";
    private static final String ATTACHMENT_LABEL_GENERIC = "Attachment analysis";

    // 메타데이터 키
    private static final String META_ATTACHMENT = "attachment";
    private static final String META_EXTRACTED_PREVIEW = "extracted_preview";
    private static final String META_ORIGINAL_FILE_NAME = "original_file_name";
    private static final String TAG_PROMPT_NAME = "langfuse.prompt.name";

    // 오퍼레이션
    private static final String OP_VISION_DIRECT = "vision-direct-media";

    // Ingestion 이벤트 타입
    private static final String EVENT_GENERATION_CREATE = "generation-create";
    private static final String EVENT_TRACE_CREATE = "trace-create";
    private static final String EVENT_SCORE_CREATE = "score-create";
    private static final int DEFAULT_INGESTION_RETRY_COUNT = 2;
    private static final int DEFAULT_INGESTION_RETRY_BACKOFF_MS = 200;

    @Qualifier("langfuseWebClient")
    private final WebClient client;
    private final LangfuseMediaClient mediaClient;
    @Qualifier("langfuseMediaHttpClient")
    private final OkHttpClient ingestionHttpClient;
    private final ObjectMapper objectMapper;

    @Value("${langfuse.ingestion.fail-open:false}")
    private boolean ingestionFailOpen;

    @Value("${langfuse.ingestion.retry-count:" + DEFAULT_INGESTION_RETRY_COUNT + "}")
    private int ingestionRetryCount;

    @Value("${langfuse.ingestion.retry-backoff-ms:" + DEFAULT_INGESTION_RETRY_BACKOFF_MS + "}")
    private int ingestionRetryBackoffMs;

    @Value("${langfuse.host:https://cloud.langfuse.com}")
    private String langfuseHost;

    @Value("${langfuse.public-key}")
    private String publicKey;

    @Value("${langfuse.secret-key}")
    private String secretKey;

    /**
     * 비전 생성물(이미지 포함) 기록
     */
    public String recordVisionGenerationWithMedia(String traceId, String modelName,
                                                  byte[] imageBytes, String contentType,
                                                  String question, String response,
                                                  long startTime, long endTime,
                                                  Usage usage) {
        return runIngestionAction("vision generation", () -> {
            validateTraceId(traceId, "vision generation");
            String resolvedContentType = resolveUploadContentType(contentType);
            String mediaId = mediaClient.uploadMedia(traceId, imageBytes, resolvedContentType, "input");

            Map<String, Object> metadata = new HashMap<>();
            if (mediaId != null) {
                metadata.put(META_ATTACHMENT, String.format(MEDIA_TOKEN_FORMAT, resolvedContentType, mediaId));
            }

            String generationId = recordFullGenerationBatch(traceId, OP_VISION_DIRECT, modelName,
                    question, response, startTime, endTime, usage, metadata);

            log.info("[INGESTION] Recorded Vision generation. Tokens: {}",
                    usage != null ? usage.getTotalTokens() : "unknown");
            return generationId;
        }, null);
    }

    /**
     * 첨부파일(PDF 등) 포함 생성물 기록
     */
    public String recordGenerationWithAttachment(String traceId, String name, String modelName,
                                                 String originalFileName, byte[] originalFileBytes, String contentType,
                                                 String question, String extractedText, String llmOutput,
                                                 long startTime, long endTime) {
        return runIngestionAction("attachment generation", () -> {
            validateTraceId(traceId, "attachment generation");
            String resolvedContentType = resolveAttachmentContentType(contentType);
            String mediaId = mediaClient.uploadMedia(traceId, originalFileBytes, resolvedContentType, "input");

            String mediaToken = (mediaId != null)
                    ? String.format(MEDIA_TOKEN_FORMAT, resolvedContentType, mediaId)
                    : MEDIA_UPLOAD_FAILED;

            Map<String, Object> metadata = new HashMap<>();
            metadata.put(META_ORIGINAL_FILE_NAME, originalFileName);
            if (mediaId != null) {
                metadata.put(META_ATTACHMENT, mediaToken);
            }
            if (extractedText != null && !extractedText.isBlank()) {
                metadata.put(META_EXTRACTED_PREVIEW,
                        extractedText.substring(0, Math.min(extractedText.length(), 1000)));
            }

            String inputContent = buildAttachmentInputContent(originalFileName, resolvedContentType, question);

            return recordFullGenerationBatch(
                    traceId,
                    name,
                    modelName,
                    inputContent,
                    llmOutput,
                    startTime,
                    endTime,
                    null,
                    metadata
            );
        }, null);
    }

    private String buildAttachmentInputContent(String originalFileName, String contentType, String question) {
        String attachmentLabel = resolveAttachmentLabel(contentType);
        if (question == null || question.isBlank()) {
            return attachmentLabel + ": " + originalFileName;
        }

        return attachmentLabel + ": " + originalFileName + "\nQuestion: " + question;
    }

    /**
     * 관측 입력(input) 가독성을 위한 라벨을 반환합니다.
     * 이 값은 Langfuse 기록용 문자열이며, 런타임 실행 분기 제어값이 아닙니다.
     */
    private String resolveAttachmentLabel(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return ATTACHMENT_LABEL_GENERIC;
        }

        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("pdf")) {
            return ATTACHMENT_LABEL_PDF;
        }
        if (normalized.startsWith("image/")) {
            return ATTACHMENT_LABEL_IMAGE;
        }
        if (normalized.startsWith("text/")) {
            return ATTACHMENT_LABEL_TEXT;
        }

        return ATTACHMENT_LABEL_GENERIC;
    }

    private String resolveAttachmentContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return LlmConstants.MEDIA_TYPE_PDF;
        }
        return contentType;
    }

    /**
     * 일반 LLM 생성 기록
     */
    public String recordGeneration(String traceId, String name, String modelName,
                                   String input, String output, String promptName,
                                   long startTime, long endTime) {
        return runIngestionAction("generation", () -> {
            validateTraceId(traceId, "generation");
            Map<String, Object> metadata = new HashMap<>();
            if (promptName != null) {
                metadata.put(TAG_PROMPT_NAME, promptName);
            }

            return recordFullGenerationBatch(traceId, name, modelName, input, output, startTime, endTime, null, metadata);
        }, null);
    }

    private String recordFullGenerationBatch(String traceId, String name, String modelName,
                                             String input, String output,
                                             long startTime, long endTime,
                                             Usage usage, Map<String, Object> metadata) {

        UsageInfo usageInfo = (usage != null)
                ? new UsageInfo(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens())
                : null;

        String generationId = UUID.randomUUID().toString();
        GenerationBody body = new GenerationBody(
                generationId,
                traceId,
                name,
                modelName,
                input,
                output,
                Instant.ofEpochMilli(startTime).toString(),
                Instant.ofEpochMilli(endTime).toString(),
                metadata,
                usageInfo
        );

        TraceBody trace = new TraceBody(
                traceId,
                name,
                Instant.ofEpochMilli(startTime).toString(),
                input,
                output,
                metadata
        );

        List<IngestionEvent> events = List.of(
                createEvent(trace, EVENT_TRACE_CREATE),
                createEvent(body, EVENT_GENERATION_CREATE)
        );
        sendBatch(events);
        return generationId;
    }

    /**
     * 점수(Score) 기록
     */
    public void recordScore(String traceId, String name, double value, String comment) {
        runIngestionAction("score", () -> {
            validateTraceId(traceId, "score");
            ScoreBody body = new ScoreBody(
                    UUID.randomUUID().toString(),
                    traceId,
                    name,
                    value,
                    comment
            );

            sendBatch(createEvent(body, EVENT_SCORE_CREATE));

            log.info("[INGESTION] Recorded Score: {} = {}", name, value);
            return null;
        }, null);
    }

    /**
     * 미디어 다운로드 (Pre-signed URL 방식)
     */
    public byte[] downloadMedia(String mediaId) {
        return mediaClient.downloadMedia(mediaId);
    }

    public String uploadMedia(String traceId, byte[] content, String contentType, String field) {
        return mediaClient.uploadMedia(traceId, content, contentType, field);
    }

    private String resolveUploadContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_UPLOAD_CONTENT_TYPE;
        }
        return contentType;
    }

    private IngestionEvent createEvent(Object body, String type) {
        return new IngestionEvent(
                UUID.randomUUID().toString(),
                type,
                Instant.now().toString(),
                body
        );
    }

    private void sendBatch(IngestionEvent event) {
        sendBatch(List.of(event));
    }

    private void sendBatch(List<IngestionEvent> events) {
        BatchPayload payload = new BatchPayload(events);
        try {
            sendBatchWithWebClient(payload);
            return;
        } catch (RuntimeException exception) {
            if (!isRetryableIngestionException(exception)) {
                throw exception;
            }
            log.warn("[INGESTION] WebClient ingestion failed. Fallback to OkHttp. cause={}", exception.getMessage());
        }

        sendBatchWithOkHttp(payload);
    }

    private void sendBatchWithWebClient(BatchPayload payload) {
        Retry retryPolicy = Retry.backoff(
                        resolveRetryCount(),
                        Duration.ofMillis(resolveRetryBackoffMs())
                )
                .filter(this::isRetryableIngestionException);

        client.post()
                .uri("/api/public/ingestion")
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .retryWhen(retryPolicy)
                .block();
    }

    private void sendBatchWithOkHttp(BatchPayload payload) {
        String endpoint = resolveIngestionEndpoint();
        String authorizationHeader = resolveAuthorizationHeader();
        byte[] requestBodyBytes = serializePayload(payload);

        RequestBody requestBody = RequestBody.create(
                requestBodyBytes,
                MediaType.get("application/json")
        );

        Request request = new Request.Builder()
                .url(endpoint)
                .header("Authorization", authorizationHeader)
                .post(requestBody)
                .build();

        try (Response response = ingestionHttpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return;
            }
            String responseBody = readResponseBodySafely(response);
            throw new IllegalStateException(
                    "Langfuse ingestion failed with status " + response.code() + ", body: " + responseBody
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Langfuse ingestion fallback failed", exception);
        }
    }

    private String resolveIngestionEndpoint() {
        String host = langfuseHost;
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("langfuse.host must not be blank");
        }
        String normalizedHost = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        return normalizedHost + "/api/public/ingestion";
    }

    private String resolveAuthorizationHeader() {
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalStateException("langfuse.public-key must not be blank");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("langfuse.secret-key must not be blank");
        }
        String encoded = Base64.getEncoder()
                .encodeToString((publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private byte[] serializePayload(BatchPayload payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Langfuse ingestion payload", exception);
        }
    }

    private String readResponseBodySafely(Response response) {
        if (response.body() == null) {
            return "";
        }
        try {
            return response.body().string();
        } catch (IOException exception) {
            return "";
        }
    }

    private <T> T runIngestionAction(String actionName, Supplier<T> action, T fallbackValue) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            log.error("[INGESTION] Failed to record {}: {}", actionName, e.getMessage(), e);
            if (ingestionFailOpen) {
                return fallbackValue;
            }
            throw new IllegalStateException("Failed to record " + actionName, e);
        }
    }

    private void validateTraceId(String traceId, String actionName) {
        if (!TraceUtils.isValid(traceId)) {
            throw new IllegalArgumentException("traceId must be valid for " + actionName);
        }
    }

    /**
     * 간헐적인 연결 종료(EOF) 계열만 재시도하고,
     * 인증/권한/검증 오류 같은 비재시도 오류는 즉시 실패시킵니다.
     */
    private boolean isRetryableIngestionException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof WebClientRequestException) {
                return true;
            }
            if (current instanceof EOFException) {
                return true;
            }
            if (current instanceof IOException ioException) {
                String message = ioException.getMessage();
                if (message != null && message.toLowerCase(Locale.ROOT).contains("header parser received no bytes")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private int resolveRetryCount() {
        if (ingestionRetryCount >= 0) {
            return ingestionRetryCount;
        }
        return DEFAULT_INGESTION_RETRY_COUNT;
    }

    private int resolveRetryBackoffMs() {
        if (ingestionRetryBackoffMs > 0) {
            return ingestionRetryBackoffMs;
        }
        return DEFAULT_INGESTION_RETRY_BACKOFF_MS;
    }
}
