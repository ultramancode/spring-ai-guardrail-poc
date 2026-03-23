package com.ultramancode.aiguardrail.common.llm.infrastructure.factory.provider;


import com.ultramancode.aiguardrail.common.llm.LlmConstants;
import com.ultramancode.aiguardrail.common.llm.LlmFactoryRequest;
import com.ultramancode.aiguardrail.common.llm.infrastructure.factory.config.OllamaLlmProperties;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Ollama 모델 Provider
 */
@Slf4j
@Component
@ConditionalOnClass(OllamaChatModel.class)
public class OllamaProvider extends AbstractLlmProvider {

    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final OllamaLlmProperties properties;

    public OllamaProvider(ObservationRegistry observationRegistry,
                          RestClient.Builder restClientBuilder,
                          WebClient.Builder webClientBuilder,
                          OllamaLlmProperties properties) {
        super(observationRegistry);
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
    }

    @Override
    public String getVendor() {
        return LlmConstants.VENDOR_OLLAMA;
    }

    @Override
    public ChatModel createModel(LlmFactoryRequest request) {
        log.debug("[OLLAMA-PROVIDER] Creating Ollama model: {}", request);

        // 1. Base URL 및 모델 결정
        String baseUrl = normalizeUrl(
                getOrDefault(request.getBaseUrl(), properties.baseUrl()),
                OllamaLlmProperties.DEFAULT_BASE_URL
        );

        String modelName = getOrDefault(request.getModel(), properties.chat().options().model());

        // 2. Ollama API 구성
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();

        // 3. 옵션 구성
        double temperature = getOrDefault(request.getTemperature(), properties.chat().options().temperature());

        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .build();

        log.info("[OLLAMA-PROVIDER] Created Ollama model: {} @ {} (temp: {})",
                modelName, baseUrl, temperature);

        // 4. Chat Model 생성
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    @Override
    protected String getPropertyModel() {
        return properties.chat().options().model();
    }

}

