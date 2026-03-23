package com.ultramancode.aiguardrail.experiment.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultramancode.aiguardrail.common.observability.LangfuseConstants;
import com.ultramancode.aiguardrail.experiment.application.port.out.EvaluationRepositoryPort;
import com.ultramancode.aiguardrail.experiment.application.result.ScoreResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseScoreQuerySupport {

    @Qualifier("langfuseWebClient")
    private final WebClient client;
    private final ObjectMapper objectMapper;

    public EvaluationRepositoryPort.ScorePageResult fetchScoresPage(
            int page,
            int limit
    ) {
        try {
            String response = client.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/public/scores")
                                .queryParam("page", page)
                                .queryParam("limit", limit);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = readTreeOrThrow(response, "scores");
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                throw new IllegalStateException("Invalid Langfuse scores response: missing data array");
            }
            if (data.isEmpty()) {
                return EvaluationRepositoryPort.ScorePageResult.empty();
            }

            List<ScoreResult> records = new ArrayList<>();
            for (JsonNode node : data) {
                Double scoreValue = resolveScoreValueOrNull(node);
                if (scoreValue == null) {
                    log.warn(
                            "[EXPERIMENT] Skip score record with invalid value. id={}, traceId={}, name={}, rawValue={}",
                            node.path(LangfuseConstants.KEY_ID).asText(""),
                            node.path(LangfuseConstants.KEY_TRACE_ID).asText(""),
                            node.path(LangfuseConstants.KEY_NAME).asText(""),
                            node.path(LangfuseConstants.KEY_VALUE).asText("")
                    );
                    continue;
                }
                records.add(ScoreResult.builder()
                        .id(node.path(LangfuseConstants.KEY_ID).asText())
                        .traceId(node.path(LangfuseConstants.KEY_TRACE_ID).asText())
                        .name(node.path(LangfuseConstants.KEY_NAME).asText())
                        .value(scoreValue)
                        .comment(node.path(LangfuseConstants.KEY_COMMENT).asText())
                        .createdAtEpochMillis(resolveCreatedAtEpochMillis(node))
                        .build());
            }
            return new EvaluationRepositoryPort.ScorePageResult(records, data.size());
        } catch (WebClientResponseException | WebClientRequestException e) {
            throw new IllegalStateException("Failed to call Langfuse scores API", e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to fetch scores page: " + page, e);
        }
    }

    private JsonNode readTreeOrThrow(String response, String operationName) {
        try {
            return objectMapper.readTree(response);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse Langfuse response for operation: " + operationName, e);
        }
    }

    private Double resolveScoreValueOrNull(JsonNode node) {
        JsonNode valueNode = node.path(LangfuseConstants.KEY_VALUE);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }

        if (valueNode.isNumber()) {
            double value = valueNode.doubleValue();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return value;
        }

        if (!valueNode.isTextual()) {
            return null;
        }

        String rawValue = valueNode.asText();
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            double value = Double.parseDouble(rawValue.trim());
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long resolveCreatedAtEpochMillis(JsonNode node) {
        String rawCreatedAt = node.path(LangfuseConstants.KEY_CREATED_AT).asText(null);
        Long createdAt = parseIsoInstant(rawCreatedAt);
        if (createdAt != null) {
            return createdAt;
        }

        String rawUpdatedAt = node.path(LangfuseConstants.KEY_UPDATED_AT).asText(null);
        Long updatedAt = parseIsoInstant(rawUpdatedAt);
        if (updatedAt != null) {
            return updatedAt;
        }

        if ((rawCreatedAt != null && !rawCreatedAt.isBlank()) || (rawUpdatedAt != null && !rawUpdatedAt.isBlank())) {
            log.warn(
                    "[EXPERIMENT] Invalid timestamp format in score payload. id={}, createdAt={}, updatedAt={}",
                    node.path(LangfuseConstants.KEY_ID).asText(""),
                    rawCreatedAt,
                    rawUpdatedAt
            );
        }
        return null;
    }

    private Long parseIsoInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed).toEpochMilli();
        } catch (DateTimeParseException e) {
            if (trimmed.chars().allMatch(Character::isDigit)) {
                try {
                    long parsed = Long.parseLong(trimmed);
                    if (trimmed.length() <= 10) {
                        return parsed * 1000L;
                    }
                    return parsed;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
    }
}
