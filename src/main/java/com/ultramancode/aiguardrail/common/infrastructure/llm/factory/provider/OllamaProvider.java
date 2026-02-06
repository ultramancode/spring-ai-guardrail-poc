package com.ultramancode.aiguardrail.common.infrastructure.llm.factory.provider;

import com.ultramancode.aiguardrail.common.infrastructure.llm.factory.DynamicChatModelFactory;
import com.ultramancode.aiguardrail.common.infrastructure.llm.factory.LlmFactoryRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Ollama 모델 Provider (로컬 LLM 지원)
 *
 * <p>spring-ai-ollama 의존성이 있을 때만 활성화됩니다.</p>
 * <p>애플리케이션 시작 시 DynamicChatModelFactory에 자동 등록됩니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(OllamaChatModel.class)
public class OllamaProvider {

    private static final String VENDOR_OLLAMA = "ollama";

    private final DynamicChatModelFactory factory;
    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String defaultBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:llama3}")
    private String defaultModel;

    @Value("${spring.ai.ollama.chat.options.temperature:0.7}")
    private Double defaultTemperature;

    @PostConstruct
    public void init() {
        factory.registerProvider(VENDOR_OLLAMA, this::createOllamaModel);
    }

    private ChatModel createOllamaModel(LlmFactoryRequest request) {
        log.debug("[OLLAMA-PROVIDER] Creating Ollama model: {}", request);

        // 1. Base URL 및 모델 결정
        String baseUrl = StringUtils.hasText(request.getBaseUrl())
                ? normalizeUrl(request.getBaseUrl())
                : normalizeUrl(defaultBaseUrl);

        String modelName = StringUtils.hasText(request.getModel())
                ? request.getModel()
                : defaultModel;

        // 2. Ollama API 구성
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();

        // 3. 옵션 구성
        Double temperature = request.getTemperature() != null
                ? request.getTemperature()
                : defaultTemperature;

        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .build();

        log.info("[OLLAMA-PROVIDER] Created Ollama model: {} at {} (temp: {})",
                modelName, baseUrl, temperature);

        // 4. Chat Model 생성
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * URL을 정규화합니다 (http:// 프리픽스 추가)
     */
    private String normalizeUrl(String url) {
        if (url == null) return "http://localhost:11434";
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "http://" + trimmed;
        }
        return trimmed;
    }
}
