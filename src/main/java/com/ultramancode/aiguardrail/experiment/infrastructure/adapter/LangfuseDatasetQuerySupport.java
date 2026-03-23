package com.ultramancode.aiguardrail.experiment.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultramancode.aiguardrail.common.observability.LangfuseConstants;
import com.ultramancode.aiguardrail.common.observability.TraceUtils;
import com.ultramancode.aiguardrail.common.util.DatasetItemSignatureUtils;
import com.ultramancode.aiguardrail.common.util.PageSignatureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseDatasetQuerySupport {

    private static final int MAX_DATASET_ITEMS_LIMIT = 100;

    private static final List<String> DATASET_TOTAL_COUNT_POINTERS = List.of(
            "/meta/totalItems",
            "/meta/total",
            "/totalItems",
            "/total",
            "/pagination/totalItems",
            "/pagination/total"
    );

    @Qualifier("langfuseWebClient")
    private final WebClient client;
    @Qualifier("langfuseMediaHttpClient")
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${langfuse.host:https://cloud.langfuse.com}")
    private String langfuseHost;

    @Value("${langfuse.public-key}")
    private String publicKey;

    @Value("${langfuse.secret-key}")
    private String secretKey;

    public Set<String> fetchTraceIdsByRunName(String runName, int datasetRunItemsLimit, int maxDatasetRunItemPages) {
        if (runName == null || runName.isBlank()) {
            return Collections.emptySet();
        }

        try {
            Set<String> traceIds = new HashSet<>();
            int page = 1;
            String previousPageSignature = null;

            while (true) {
                if (page > maxDatasetRunItemPages) {
                    throw new IllegalStateException("Exceeded max dataset-run-item pages: " + maxDatasetRunItemPages);
                }

                final int currentPage = page;
                String response = getJsonWithFallback(
                        "/api/public/dataset-run-items",
                        buildQueryParams(
                                "page",
                                String.valueOf(currentPage),
                                "limit",
                                String.valueOf(datasetRunItemsLimit)
                        ),
                        "dataset-run-items"
                );

                JsonNode root = readTreeOrThrow(response, "dataset-run-items");
                JsonNode data = root.path("data");
                if (!data.isArray() || data.isEmpty()) {
                    break;
                }

                String currentPageSignature = buildDatasetRunItemsPageSignature(data);
                if (currentPageSignature != null && currentPageSignature.equals(previousPageSignature)) {
                    log.warn("[EXPERIMENT] Detected repeated dataset-run-items page. stop paging. page={}, runName={}",
                            currentPage, runName);
                    break;
                }
                previousPageSignature = currentPageSignature;

                for (JsonNode node : data) {
                    String currentRunName = node.path(LangfuseConstants.KEY_RUN_NAME).asText();
                    if (!runName.equals(currentRunName)) {
                        continue;
                    }

                    String traceId = node.path(LangfuseConstants.KEY_TRACE_ID).asText();
                    if (TraceUtils.isValid(traceId)) {
                        traceIds.add(traceId);
                    }
                }

                if (data.size() < datasetRunItemsLimit) {
                    break;
                }
                page++;
            }
            return traceIds;
        } catch (WebClientResponseException | WebClientRequestException e) {
            throw new IllegalStateException("Failed to call Langfuse dataset-run-items API", e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to fetch traceIds by runName: " + runName, e);
        }
    }

    public List<Map<String, Object>> fetchDatasetItems(
            String datasetName,
            int datasetItemsLimit,
            int maxDatasetItemPages
    ) {
        List<Map<String, Object>> items = new ArrayList<>();
        forEachDatasetItemsPage(datasetName, datasetItemsLimit, maxDatasetItemPages, items::addAll);
        return items;
    }

    public List<Map<String, Object>> fetchDatasetItemsPage(String datasetName, int page, int limit) {
        if (datasetName == null || datasetName.isBlank()) {
            return Collections.emptyList();
        }
        if (page <= 0) {
            throw new IllegalArgumentException("page must be greater than zero");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }

        JsonNode root = fetchDatasetItemsRoot(datasetName, page, limit);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            throw new IllegalStateException("Invalid Langfuse dataset response: missing data array");
        }
        if (data.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode node : data) {
            items.add(objectMapper.convertValue(node, new TypeReference<>() {
            }));
        }
        return items;
    }

    public int countDatasetItems(String datasetName, int datasetItemsLimit, int maxDatasetItemPages) {
        if (datasetName == null || datasetName.isBlank()) {
            return 0;
        }

        Integer totalCountFromMetadata = resolveTotalCountFromMetadata(datasetName);
        if (totalCountFromMetadata != null) {
            return totalCountFromMetadata;
        }

        final int[] totalCount = {0};
        forEachDatasetItemsPage(
                datasetName,
                datasetItemsLimit,
                maxDatasetItemPages,
                pageItems -> totalCount[0] += pageItems.size()
        );
        return totalCount[0];
    }

    private void forEachDatasetItemsPage(
            String datasetName,
            int datasetItemsLimit,
            int maxDatasetItemPages,
            Consumer<List<Map<String, Object>>> pageConsumer
    ) {
        int page = 1;
        String previousPageSignature = null;

        while (true) {
            if (page > maxDatasetItemPages) {
                throw new IllegalStateException("Exceeded max dataset-item pages: " + maxDatasetItemPages);
            }

            List<Map<String, Object>> pageItems = fetchDatasetItemsPage(datasetName, page, datasetItemsLimit);
            if (pageItems.isEmpty()) {
                break;
            }

            String currentPageSignature = PageSignatureUtils.buildPageSignature(
                    pageItems,
                    DatasetItemSignatureUtils::resolveSignature
            );
            if (currentPageSignature != null && currentPageSignature.equals(previousPageSignature)) {
                log.warn(
                        "[EXPERIMENT] Detected repeated dataset-items page. stop paging. page={}, datasetName={}",
                        page,
                        datasetName
                );
                break;
            }
            previousPageSignature = currentPageSignature;

            pageConsumer.accept(pageItems);
            if (pageItems.size() < datasetItemsLimit) {
                break;
            }
            page++;
        }
    }

    private Integer resolveTotalCountFromMetadata(String datasetName) {
        try {
            JsonNode root = fetchDatasetItemsRoot(datasetName, 1, 1);
            JsonNode data = root.path("data");
            if (data.isArray() && data.isEmpty()) {
                return 0;
            }

            Integer extractedTotal = extractTotalCount(root);
            if (extractedTotal != null) {
                log.debug(
                        "[EXPERIMENT] Resolved dataset item count from metadata. datasetName={}, total={}",
                        datasetName,
                        extractedTotal
                );
            }
            return extractedTotal;
        } catch (IllegalStateException e) {
            log.debug(
                    "[EXPERIMENT] Failed metadata count extraction. Falling back to paged counting. datasetName={}, cause={}",
                    datasetName,
                    e.getMessage(),
                    e
            );
            return null;
        }
    }

    private Integer extractTotalCount(JsonNode root) {
        for (String pointer : DATASET_TOTAL_COUNT_POINTERS) {
            JsonNode candidate = root.at(pointer);
            Integer resolvedCount = asNonNegativeInteger(candidate);
            if (resolvedCount != null) {
                return resolvedCount;
            }
        }
        return null;
    }

    private Integer asNonNegativeInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (node.isIntegralNumber()) {
            long value = node.longValue();
            if (value < 0 || value > Integer.MAX_VALUE) {
                return null;
            }
            return (int) value;
        }

        if (node.isTextual()) {
            String text = node.asText();
            if (text == null || text.isBlank()) {
                return null;
            }

            try {
                long value = Long.parseLong(text.trim());
                if (value < 0 || value > Integer.MAX_VALUE) {
                    return null;
                }
                return (int) value;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    private JsonNode fetchDatasetItemsRoot(String datasetName, int page, int limit) {
        try {
            int normalizedLimit = normalizeDatasetItemsLimit(limit);
            String response = getJsonWithFallback(
                    "/api/public/dataset-items",
                    buildQueryParams(
                            "datasetName",
                            datasetName,
                            "page",
                            String.valueOf(page),
                            "limit",
                            String.valueOf(normalizedLimit)
                    ),
                    "dataset-items"
            );

            return readTreeOrThrow(response, "dataset-items");
        } catch (WebClientResponseException | WebClientRequestException e) {
            log.error(
                    "[EXPERIMENT] Failed to fetch dataset from Langfuse API. datasetName={}, page={}, limit={}",
                    datasetName,
                    page,
                    limit,
                    e
            );
            throw new IllegalStateException("Failed to fetch dataset items from Langfuse API", e);
        } catch (RuntimeException e) {
            log.error(
                    "[EXPERIMENT] Failed to fetch dataset. datasetName={}, page={}, limit={}",
                    datasetName,
                    page,
                    limit,
                    e
            );
            throw new IllegalStateException("Failed to fetch dataset items from Langfuse", e);
        }
    }

    private int normalizeDatasetItemsLimit(int limit) {
        if (limit <= MAX_DATASET_ITEMS_LIMIT) {
            return limit;
        }

        log.warn(
                "[EXPERIMENT] dataset-items limit {} exceeds Langfuse max {}. Clamp to max.",
                limit,
                MAX_DATASET_ITEMS_LIMIT
        );
        return MAX_DATASET_ITEMS_LIMIT;
    }

    private String getJsonWithFallback(String path, Map<String, String> queryParams, String operationName) {
        try {
            return client.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path(path);
                        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                            builder.queryParam(entry.getKey(), entry.getValue());
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (RuntimeException e) {
            if (!isRetryableTransportException(e)) {
                throw e;
            }

            log.warn(
                    "[EXPERIMENT] WebClient {} call failed. fallback=okhttp, cause={}",
                    operationName,
                    e.getMessage()
            );
            return getJsonWithOkHttp(path, queryParams, operationName);
        }
    }

    private String getJsonWithOkHttp(String path, Map<String, String> queryParams, String operationName) {
        HttpUrl url = buildHttpUrl(path, queryParams);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", resolveAuthorizationHeader())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = readResponseBodySafely(response);
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                        "Failed to call Langfuse " + operationName + " API. status="
                                + response.code() + ", body=" + responseBody
                );
            }
            return responseBody;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call Langfuse " + operationName + " API", e);
        }
    }

    private HttpUrl buildHttpUrl(String path, Map<String, String> queryParams) {
        String host = normalizeHost(langfuseHost);
        HttpUrl baseUrl = HttpUrl.parse(host + path);
        if (baseUrl == null) {
            throw new IllegalStateException("Invalid Langfuse host or path: " + host + path);
        }

        HttpUrl.Builder builder = baseUrl.newBuilder();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            builder.addQueryParameter(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("langfuse.host must not be blank");
        }
        if (host.endsWith("/")) {
            return host.substring(0, host.length() - 1);
        }
        return host;
    }

    private String resolveAuthorizationHeader() {
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalStateException("langfuse.public-key must not be blank");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("langfuse.secret-key must not be blank");
        }

        String credential = publicKey + ":" + secretKey;
        String encoded = Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private String readResponseBodySafely(Response response) {
        if (response.body() == null) {
            return "";
        }
        try {
            return response.body().string();
        } catch (IOException ignored) {
            return "";
        }
    }

    private boolean isRetryableTransportException(Throwable throwable) {
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

    private Map<String, String> buildQueryParams(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("query params must be key/value pairs");
        }

        Map<String, String> queryParams = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = pairs[i];
            String value = pairs[i + 1];
            queryParams.put(key, value);
        }
        return queryParams;
    }

    private JsonNode readTreeOrThrow(String response, String operationName) {
        try {
            return objectMapper.readTree(response);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse Langfuse response for operation: " + operationName, e);
        }
    }

    private String buildDatasetRunItemsPageSignature(JsonNode data) {
        if (data == null || !data.isArray() || data.isEmpty()) {
            return null;
        }

        StringBuilder signatureBuilder = new StringBuilder();
        signatureBuilder.append(data.size());

        for (JsonNode node : data) {
            appendSignaturePart(signatureBuilder, node.path(LangfuseConstants.KEY_ID).asText(""));
            appendSignaturePart(signatureBuilder, node.path(LangfuseConstants.KEY_TRACE_ID).asText(""));
            appendSignaturePart(signatureBuilder, node.path(LangfuseConstants.KEY_RUN_NAME).asText(""));
            appendSignaturePart(signatureBuilder, node.path("observationId").asText(""));
        }
        return signatureBuilder.toString();
    }

    private void appendSignaturePart(StringBuilder signatureBuilder, String value) {
        String safeValue = value;
        if (safeValue == null) {
            safeValue = "";
        }

        signatureBuilder
                .append("|")
                .append(safeValue.length())
                .append(":")
                .append(safeValue);
    }

}
