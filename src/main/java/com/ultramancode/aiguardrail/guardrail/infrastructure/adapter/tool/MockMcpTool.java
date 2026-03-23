package com.ultramancode.aiguardrail.guardrail.infrastructure.adapter.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 데모용 Mock MCP 도구입니다.
 * 사용자 및 주소 데이터베이스가 포함된 외부 MCP 서버를 시뮬레이션합니다.
 */
@Slf4j
@Component
public class MockMcpTool {

    private static final Map<String, String> ADDRESS_DB = Map.of(
            "010-2345-6789", "서울시 강남구",
            "010-1234-5678", "서울시 관악구 봉천동"
    );

    private static final Map<String, String> USER_DB = Map.of(
            "김태웅", "test12345"
    );

    @Tool(description = "Search address by name and phone")
    public String searchAddress(String name, String phone) {
        log.info("[MCP] searchAddress: name={}, phone={}", name, phone);
        boolean nameMatch = USER_DB.containsKey(name);
        boolean phoneMatch = ADDRESS_DB.containsKey(phone);
        if (nameMatch && phoneMatch) {
            return "{\"status\": \"SUCCESS\", \"address\": \"" + ADDRESS_DB.get(phone) + "\"}";
        }
        return "{\"status\": \"FAILED\"}";
    }

    @Tool(description = "Search user by name")
    public String searchUserByName(String name) {
        log.info("[MCP] searchUserByName: name={}", name);
        return "{\"info\": \"" + USER_DB.getOrDefault(name, "Not found") + "\"}";
    }

    @Tool(description = "Verify user identity")
    public String verifyUser(String name, String phone) {
        log.info("[MCP] verifyUser: name={}, phone={}", name, phone);
        if (USER_DB.containsKey(name) && ADDRESS_DB.containsKey(phone)) {
            return "{\"status\": \"VERIFIED\", \"userId\": \"test12345\"}";
        }
        return "{\"status\": \"FAILED\"}";
    }
}
