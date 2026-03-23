package com.ultramancode.aiguardrail.common.integration.langfuse.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Configuration
public class LangfuseClientConfig {

    @Value("${langfuse.host:https://cloud.langfuse.com}")
    private String langfuseHost;

    @Value("${langfuse.public-key}")
    private String publicKey;

    @Value("${langfuse.secret-key}")
    private String secretKey;

    @Value("${langfuse.http.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${langfuse.http.response-timeout-ms:7000}")
    private int responseTimeoutMs;

    @Value("${langfuse.http.read-timeout-ms:7000}")
    private int readTimeoutMs;

    @Value("${langfuse.http.write-timeout-ms:7000}")
    private int writeTimeoutMs;

    @Bean
    @Qualifier("langfuseWebClient")
    public WebClient langfuseWebClient() {
        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        ExchangeFilterFunction timeoutFilter = (request, next) ->
                next.exchange(request).timeout(Duration.ofMillis(responseTimeoutMs));

        return WebClient.builder()
                .baseUrl(langfuseHost)
                .defaultHeader("Authorization", authHeader)
                .clientConnector(new JdkClientHttpConnector(httpClient))
                .filter(timeoutFilter)
                .build();
    }

    @Bean
    @Qualifier("langfuseMediaHttpClient")
    public OkHttpClient langfuseMediaHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(responseTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }
}
