package com.ultramancode.aiguardrail.common.integration.langfuse.config;

import com.langfuse.client.LangfuseClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangfuseConfig {

    @Value("${langfuse.public-key}")
    private String publicKey;

    @Value("${langfuse.secret-key}")
    private String secretKey;

    @Value("${langfuse.host:https://cloud.langfuse.com}")
    private String host;

    @Bean
    public LangfuseClient langfuse() {
        return LangfuseClient.builder()
                .url(host)
                .credentials(publicKey, secretKey)
                .build();
    }
}
