package com.ultramancode.aiguardrail.common.llm.infrastructure.factory.provider;

import com.google.genai.Client;
import com.ultramancode.aiguardrail.common.llm.LlmConstants;
import com.ultramancode.aiguardrail.common.llm.LlmFactoryRequest;
import com.ultramancode.aiguardrail.common.llm.infrastructure.factory.config.GoogleLlmProperties;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Google 모델 Provider
 */
@Slf4j
@Component
public class GeminiProvider extends AbstractLlmProvider {

    private final GoogleLlmProperties properties;

    public GeminiProvider(ObservationRegistry observationRegistry, GoogleLlmProperties properties) {
        super(observationRegistry);
        this.properties = properties;
    }

    @Override
    public String getVendor() {
        return LlmConstants.VENDOR_GOOGLE;
    }

    @Override
    public ChatModel createModel(LlmFactoryRequest request) {
        log.debug("[GOOGLE-PROVIDER] Creating Google GenAI model: {}", request);

        // 1. API Key 결정
        String apiKey = getOrDefault(request.getApiKey(), properties.apiKey());

        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "Gemini API key not configured. Set spring.ai.google.genai.api-key or provide apiKey in request.");
        }

        // 2. 모델 결정
        String model = getOrDefault(request.getModel(), properties.chat().options().model());

        // 3. Google GenAI 클라이언트 생성
        Client client = Client.builder()
                .apiKey(apiKey)
                .build();

        // 4. Temperature 결정
        double temperature = getOrDefault(request.getTemperature(), properties.chat().options().temperature());

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();

        log.info("[GOOGLE-PROVIDER] Created Google GenAI model: {} (temp: {})", model, temperature);

        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .defaultOptions(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    @Override
    protected String getPropertyModel() {
        return properties.chat().options().model();
    }

}

