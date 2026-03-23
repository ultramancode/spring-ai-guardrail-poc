package com.ultramancode.aiguardrail.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class StableMapSignatureUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String buildStableHash(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        try {
            JsonNode rawTree = OBJECT_MAPPER.valueToTree(payload);
            JsonNode canonicalTree = canonicalize(rawTree);
            String canonicalJson = OBJECT_MAPPER.writeValueAsString(canonicalTree);
            return toSha256Hex(canonicalJson);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize SHA-256 digest", e);
        } catch (JsonProcessingException | RuntimeException e) {
            if (e instanceof JsonProcessingException) {
                log.warn("[STABLE-HASH] Failed to serialize canonical payload. cause={}", e.getMessage(), e);
            } else {
                log.warn("[STABLE-HASH] Failed to build stable hash. cause={}", e.getMessage(), e);
            }
            return null;
        }
    }

    private static JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }

        if (node.isObject()) {
            ObjectNode canonicalObject = OBJECT_MAPPER.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);

            for (String fieldName : fieldNames) {
                canonicalObject.set(fieldName, canonicalize(node.get(fieldName)));
            }
            return canonicalObject;
        }

        if (node.isArray()) {
            ArrayNode canonicalArray = OBJECT_MAPPER.createArrayNode();
            for (JsonNode child : node) {
                canonicalArray.add(canonicalize(child));
            }
            return canonicalArray;
        }

        return node;
    }

    private static String toSha256Hex(String value) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexBuilder = new StringBuilder(hashBytes.length * 2);
        for (byte hashByte : hashBytes) {
            hexBuilder.append(String.format("%02x", hashByte));
        }
        return hexBuilder.toString();
    }
}
