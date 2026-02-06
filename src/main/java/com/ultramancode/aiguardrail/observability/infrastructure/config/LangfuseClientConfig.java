package com.ultramancode.aiguardrail.observability.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;

/**
 * Langfuse REST API 연동 설정
 * <p>
 * [책임]
 * 1. WebClient 빈 생성 및 공통 헤더(Auth) 설정
 * 2. 각 클라이언트에서 재사용할 수 있도록 제공
 */
@Configuration
public class LangfuseClientConfig {

    @Value("${langfuse.host}")
    private String langfuseHost;

    @Value("${langfuse.public-key}")
    private String publicKey;

    @Value("${langfuse.secret-key}")
    private String secretKey;

    @Bean
    @Qualifier("langfuseWebClient")
    public WebClient langfuseWebClient() {
        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((publicKey + ":" + secretKey).getBytes());

        return WebClient.builder()
                .baseUrl(langfuseHost)
                .defaultHeader("Authorization", authHeader)
                .build();
    }
}
