package com.ultramancode.aiguardrail.common.infrastructure.llm.factory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 요청된 벤더에 따라 ChatModel을 동적으로 생성하는 팩토리 클래스입니다.
 *
 * <p>각 Provider는 @PostConstruct에서 이 Factory에 자신을 등록합니다.</p>
 *
 * <pre>
 * // 사용 예시
 * ChatModel model = factory.createChatModel(
 *     LlmFactoryRequest.builder().vendor("gemini").build()
 * );
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicChatModelFactory {

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final ChatClientObservationConvention chatClientObservationConvention;

    @Nullable
    private final AdvisorObservationConvention advisorObservationConvention;

    private final Map<String, Function<LlmFactoryRequest, ChatModel>> providers = new ConcurrentHashMap<>();

    /**
     * LLM Provider를 등록합니다.
     *
     * @param vendor  벤더명 (gemini, ollama, openai 등)
     * @param factory ChatModel 생성 함수
     */
    public void registerProvider(String vendor, Function<LlmFactoryRequest, ChatModel> factory) {
        providers.put(vendor.toLowerCase(), factory);
        log.info("[LLM-FACTORY] Registered provider: {}", vendor);
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

        Function<LlmFactoryRequest, ChatModel> factory = providers.get(vendor.toLowerCase());
        if (factory == null) {
            throw new IllegalArgumentException("Unsupported vendor: " + vendor +
                    ". Available vendors: " + providers.keySet());
        }

        log.debug("[LLM-FACTORY] Creating ChatModel for vendor: {}, model: {}",
                vendor, request.getModel());
        return factory.apply(request);
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
        // 스프링 AI의 관측(Observability) 설정을 모두 포함하여 빌드. 도구 호출 이름(tool_call) 등 Langfuse 표기
        return ChatClient.builder(model, observationRegistry,
                chatClientObservationConvention, advisorObservationConvention).build();
    }

    /**
     * 등록된 벤더 목록을 반환합니다.
     */
    public java.util.Set<String> getAvailableVendors() {
        return providers.keySet();
    }
}
