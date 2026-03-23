package com.ultramancode.aiguardrail.common.integration.langfuse.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Langfuse Ingestion API 요청/응답 DTO.
 * 각 Record는 Langfuse REST API의 JSON body 구조에 대응합니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LangfuseIngestionDto {

    private LangfuseIngestionDto() {
    }

    // ── Ingestion Batch 전송 ──

    /**
     * Ingestion API 최상위 Batch 페이로드
     */
    public record BatchPayload(
            List<IngestionEvent> batch
    ) {
    }

    /**
     * 단일 Ingestion 이벤트 래퍼
     */
    public record IngestionEvent(
            String id,
            String type,
            String timestamp,
            Object body
    ) {
    }

    // ── 생성 이벤트 ──

    /**
     * Generation 생성 이벤트 본문
     */
    public record GenerationBody(
            String id,
            @JsonProperty("traceId") String traceId,
            String name,
            String model,
            String input,
            String output,
            @JsonProperty("startTime") String startTime,
            @JsonProperty("endTime") String endTime,
            Map<String, Object> metadata,
            UsageInfo usage
    ) {
    }

    /**
     * 토큰 사용량 정보
     */
    public record UsageInfo(
            @JsonProperty("promptTokens") long promptTokens,
            @JsonProperty("completionTokens") long completionTokens,
            @JsonProperty("totalTokens") long totalTokens
    ) {
    }

    // ── 트레이스 이벤트 ──

    /**
     * Trace 생성 이벤트 본문
     */
    public record TraceBody(
            String id,
            String name,
            String timestamp,
            String input,
            String output,
            Map<String, Object> metadata
    ) {
    }

    // ── 점수 이벤트 ──

    /**
     * Score 생성 이벤트 본문
     */
    public record ScoreBody(
            String id,
            @JsonProperty("traceId") String traceId,
            String name,
            double value,
            String comment
    ) {
    }

    // ── 미디어 이벤트 ──

    /**
     * 미디어 업로드 초기화 요청
     */
    public record MediaInitRequest(
            @JsonProperty("traceId") String traceId,
            @JsonProperty("contentType") String contentType,
            @JsonProperty("contentLength") int contentLength,
            @JsonProperty("sha256Hash") String sha256Hash,
            String field
    ) {
    }

    /**
     * 미디어 업로드 상태 업데이트
     */
    public record MediaStatusUpdate(
            @JsonProperty("uploadedAt") String uploadedAt,
            @JsonProperty("uploadHttpStatus") int uploadHttpStatus
    ) {
    }
}
