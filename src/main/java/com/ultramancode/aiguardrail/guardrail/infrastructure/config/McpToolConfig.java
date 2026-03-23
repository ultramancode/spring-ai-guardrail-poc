package com.ultramancode.aiguardrail.guardrail.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import com.ultramancode.aiguardrail.guardrail.infrastructure.adapter.tool.MockMcpTool;
import com.ultramancode.aiguardrail.guardrail.infrastructure.adapter.tool.PiiToolCallbackWrapper;
import io.micrometer.observation.ObservationRegistry;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP 도구 및 PII 보안 래퍼 설정
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
            ObjectMapper objectMapper,
            ObservationRegistry observationRegistry,
            @Value("${guardrail.pii.audit-mode:false}") boolean auditMode,
            @Value("${guardrail.pii.trace-raw-content:false}") boolean traceRawContent
    ) {
        List<ToolCallback> allTools = new ArrayList<>();

        // 1. 내부 Mock 도구
        // 현재는 MCP 서버 실연동 모드로 고정하기 위해 Mock 도구 등록을 비활성화합니다.
//        List<ToolCallback> mockCallback = Arrays.stream(ToolCallbacks.from(mockTool))
//                .map(callback -> new PiiToolCallbackWrapper(
//                        callback,
//                        piiService,
//                        objectMapper,
//                        observationRegistry,
//                        auditMode,
//                        traceRawContent
//                ))
//                .collect(Collectors.toList());
//        allTools.addAll(mockCallback);
//        log.info("[MCP-CONFIG] Registered {} Mock tools", mockCallback.size());
        log.info("[MCP-CONFIG] Mock tools are disabled. Using MCP server tools only.");

        // 2. 외부 MCP 도구
        // 주의: 애플리케이션 시작 시 MCP 서버로 네트워크 호출을 수행합니다.
        if (mcpClients != null && !mcpClients.isEmpty()) {
            for (McpSyncClient mcpClient : mcpClients) {
                try {
                    List<Tool> tools = mcpClient.listTools(null).tools();
                    List<ToolCallback> callbacks = tools.stream()
                            .map(tool -> SyncMcpToolCallback.builder()
                                    .mcpClient(mcpClient)
                                    .tool(tool)
                                    .build())
                            .map(callback -> new PiiToolCallbackWrapper(
                                    callback,
                                    piiService,
                                    objectMapper,
                                    observationRegistry,
                                    auditMode,
                                    traceRawContent
                            ))
                            .collect(Collectors.toList());
                    allTools.addAll(callbacks);

                    log.info("[MCP-CONFIG] Connected to MCP Client. Found {} tools.", callbacks.size());
                } catch (RuntimeException e) {
                    log.error(
                            "[MCP-CONFIG] Failed to list tools from MCP Client. clientType={}, clientId={}, cause={}",
                            mcpClient.getClass().getName(),
                            System.identityHashCode(mcpClient),
                            e.getMessage(),
                            e
                    );
                }
            }
        }

        return allTools;
    }
}
