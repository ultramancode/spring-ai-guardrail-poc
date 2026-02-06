package com.ultramancode.aiguardrail.global;

import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.guardrail.infrastructure.adapter.tool.PiiToolCallbackWrapper;
import com.ultramancode.aiguardrail.guardrail.infrastructure.adapter.tool.MockMcpTool;
import io.micrometer.observation.ObservationRegistry;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP 도구 및 PII 보안 래퍼 설정
 * <p>
 * [역할]
 * - 외부 MCP 서버 연결 및 도구 목록 조회 (Startup 시점)
 * - PII 가드레일(Wrapper) 적용
 * - 컨트롤러에 주입할 최종 도구 리스트 생성
 */
@Slf4j
@Configuration
public class McpToolConfig {

    @Bean
    public List<ToolCallback> piiSecuredTools(
            MockMcpTool mockTool,
            List<McpSyncClient> mcpClients,
            PiiUseCase piiService,
            ObservationRegistry observationRegistry,
            @Value("${guardrail.pii.audit-mode:false}") boolean auditMode
    ) {
        List<ToolCallback> allTools = new ArrayList<>();

        // 1. Mock Tools (Internal)
        List<ToolCallback> mockCallback = Arrays.stream(ToolCallbacks.from(mockTool))
                .map(callback -> new PiiToolCallbackWrapper(callback, piiService, observationRegistry, auditMode))
                .collect(Collectors.toList());
        allTools.addAll(mockCallback);
        log.info("[MCP-CONFIG] Registered {} Mock tools", mockCallback.size());

        // 2. Real MCP Tools (External)
        // Warning: This performs network calls to MCP servers during startup.
        if (mcpClients != null && !mcpClients.isEmpty()) {
            for (McpSyncClient mcpClient : mcpClients) {
                try {
                    var tools = mcpClient.listTools(null).tools();
                    var callbacks = tools.stream()
                            .map(tool -> new SyncMcpToolCallback(mcpClient, tool))
                            .map(callback -> new PiiToolCallbackWrapper(callback, piiService, observationRegistry,
                                    auditMode))
                            .collect(Collectors.toList());
                    allTools.addAll(callbacks);

                    log.info("[MCP-CONFIG] Connected to MCP Client. Found {} tools.", callbacks.size());
                } catch (Exception e) {
                    log.error("[MCP-CONFIG] Failed to list tools from MCP Client: {}", e.getMessage());
                }
            }
        }

        return allTools;
    }
}
