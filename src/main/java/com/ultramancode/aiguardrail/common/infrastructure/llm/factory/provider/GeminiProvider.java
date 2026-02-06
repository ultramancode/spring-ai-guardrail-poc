package com.ultramancode.aiguardrail.common.infrastructure.llm.factory.provider;

import com.google.genai.Client;
import com.ultramancode.aiguardrail.common.infrastructure.llm.factory.DynamicChatModelFactory;
import com.ultramancode.aiguardrail.common.infrastructure.llm.factory.LlmFactoryRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Google Gemini 모델 Provider
 *
 * <p>애플리케이션 시작 시 DynamicChatModelFactory에 자동 등록됩니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiProvider {

    private static final String VENDOR_GEMINI = "gemini";
    private static final String VENDOR_GOOGLE = "google";

    private final DynamicChatModelFactory factory;

    @Value("${spring.ai.google.genai.api-key:}")
    private String defaultApiKey;

    @Value("${spring.ai.google.genai.chat.options.model:gemini-2.0-flash}")
    private String defaultModel;

    @Value("${spring.ai.google.genai.chat.options.temperature:0.7}")
    private Double defaultTemperature;

    @PostConstruct
    public void init() {
        factory.registerProvider(VENDOR_GEMINI, this::createGeminiModel);
        factory.registerProvider(VENDOR_GOOGLE, this::createGeminiModel);
    }

    private ChatModel createGeminiModel(LlmFactoryRequest request) {
        log.debug("[GEMINI-PROVIDER] Creating Gemini model: {}", request);

        // 1. API Key 결정 (요청값 우선, 없으면 기본값)
        String apiKey = StringUtils.hasText(request.getApiKey())
                ? request.getApiKey()
                : defaultApiKey;

        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "Gemini API key not configured. Set spring.ai.google.genai.api-key or provide apiKey in request.");
        }

        // 2. 모델 결정
        String model = StringUtils.hasText(request.getModel())
                ? request.getModel()
                : defaultModel;

        // 3. Google GenAI 클라이언트 생성
        Client client = Client.builder()
                .apiKey(apiKey)
                .build();

        // 4. 온도(Temperature) 결정
        Double temperature = request.getTemperature() != null
                ? request.getTemperature()
                : defaultTemperature;

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();

        log.info("[GEMINI-PROVIDER] Created Gemini model: {} (temp: {})", model, temperature);

        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .defaultOptions(options)
                .build();
    }
}
