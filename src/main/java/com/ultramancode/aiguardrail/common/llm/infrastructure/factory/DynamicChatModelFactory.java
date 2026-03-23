package com.ultramancode.aiguardrail.common.llm.infrastructure.factory;

import com.ultramancode.aiguardrail.common.llm.LlmFactoryRequest;
import com.ultramancode.aiguardrail.common.llm.infrastructure.factory.provider.LlmProvider;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 요청된 벤더에 따라 ChatModel을 동적으로 생성하는 팩토리 클래스입니다.
 * Spring이 주입한 List of LlmProvider를 생성자에서 자동 등록합니다.
 * createChatModel 호출로 벤더별 모델 인스턴스를 생성합니다.
 */
@Slf4j
@Service
public class DynamicChatModelFactory {

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final ChatClientObservationConvention chatClientObservationConvention;

    @Nullable
    private final AdvisorObservationConvention advisorObservationConvention;

    private final Map<String, LlmProvider> providerMap = new ConcurrentHashMap<>();

    public DynamicChatModelFactory(ObservationRegistry observationRegistry,
                                   @Nullable ChatClientObservationConvention chatClientObservationConvention,
                                   @Nullable AdvisorObservationConvention advisorObservationConvention,
                                   List<LlmProvider> llmProviders) {
        this.observationRegistry = observationRegistry;
        this.chatClientObservationConvention = chatClientObservationConvention;
        this.advisorObservationConvention = advisorObservationConvention;

        // 자동 등록
        if (llmProviders != null) {
            llmProviders.forEach(this::registerProvider);
        }
    }

    /**
     * LLM Provider를 등록합니다.
     *
     * @param provider 등록할 LLM Provider 인스턴스
     */
    public void registerProvider(LlmProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("LLM provider must not be null");
        }

        String vendor = provider.getVendor();
        if (vendor == null || vendor.isBlank()) {
            throw new IllegalArgumentException("LLM provider vendor must not be blank");
        }

        String normalizedVendor = vendor.toLowerCase(Locale.ROOT);
        LlmProvider previousProvider = providerMap.putIfAbsent(normalizedVendor, provider);
        if (previousProvider != null) {
            throw new IllegalStateException(
                    "Duplicate LLM provider registration detected. vendor=" + normalizedVendor
                            + ", existing=" + previousProvider.getClass().getName()
                            + ", incoming=" + provider.getClass().getName()
            );
        }

        log.info("[LLM-FACTORY] Registered provider: {}", normalizedVendor);
    }

    /**
     * 요청 정보를 기반으로 새로운 ChatModel 인스턴스를 생성합니다.
     *
     * @param request 벤더, 모델, API Key 등이 포함된 요청
     * @return 생성된 ChatModel
     * @throws IllegalArgumentException 지원하지 않는 벤더인 경우
     */
    public ChatModel createChatModel(LlmFactoryRequest request) {
        String vendor = request.getVendor();
        if (vendor == null) {
            throw new IllegalArgumentException("Vendor must be specified");
        }

        LlmProvider provider = providerMap.get(vendor.toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported vendor: " + vendor +
                    ". Available vendors: " + providerMap.keySet());
        }

        log.debug("[LLM-FACTORY] Creating ChatModel for vendor: {}, model: {}",
                vendor, request.getModel());
        return provider.createModel(request);
    }

    /**
     * ChatModel을 ChatClient로 래핑하여 반환합니다.
     * Advisor 없이 순수한 ChatClient를 생성합니다.
     *
     * @param request 벤더, 모델 등이 포함된 요청
     * @return 생성된 ChatClient
     */
    public ChatClient createChatClient(LlmFactoryRequest request) {
        ChatModel model = createChatModel(request);
        // 스프링 AI의 관측(Observability) 설정을 모두 포함하여 빌드.
        return ChatClient.builder(model, observationRegistry,
                chatClientObservationConvention, advisorObservationConvention).build();
    }

    /**
     * 등록된 벤더 목록을 반환합니다.
     */
    public Set<String> getAvailableVendors() {
        return providerMap.keySet();
    }

    /**
     * 벤더명으로 Provider 인스턴스를 반환합니다.
     */
    public LlmProvider getProvider(String vendor) {
        if (vendor == null) {
            return null;
        }
        return providerMap.get(vendor.toLowerCase(Locale.ROOT));
    }
}
