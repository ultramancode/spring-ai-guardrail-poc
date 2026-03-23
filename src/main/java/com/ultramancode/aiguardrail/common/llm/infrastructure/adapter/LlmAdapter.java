package com.ultramancode.aiguardrail.common.llm.infrastructure.adapter;

import com.ultramancode.aiguardrail.common.llm.LlmFactoryRequest;
import com.ultramancode.aiguardrail.common.llm.application.port.out.LlmPort;
import com.ultramancode.aiguardrail.common.llm.infrastructure.factory.DynamicChatModelFactory;
import com.ultramancode.aiguardrail.common.llm.infrastructure.factory.config.LlmProperties;
import com.ultramancode.aiguardrail.common.llm.infrastructure.factory.provider.LlmProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LlmAdapter implements LlmPort {

    private final DynamicChatModelFactory chatModelFactory;
    private final LlmProperties llmProperties;

    @Override
    public ChatClient getChatClient(String vendor, String model) {
        String resolvedVendor = normalizeVendor(vendor);
        String resolvedModel = normalizeModel(model);

        return chatModelFactory.createChatClient(
                LlmFactoryRequest.builder()
                        .vendor(resolvedVendor)
                        .model(resolvedModel)
                        .build()
        );
    }

    @Override
    public ChatClient getDefaultChatClient() {
        return getChatClient(llmProperties.defaultVendor().getValue(), null);
    }

    @Override
    public String getModelName(String vendor) {
        if (vendor == null) {
            return getDefaultModelName();
        }

        LlmProvider provider = chatModelFactory.getProvider(vendor);
        if (provider != null) {
            return provider.getDefaultModelName();
        }
        return "unknown";
    }

    @Override
    public String getDefaultModelName() {
        return getModelName(llmProperties.defaultVendor().getValue());
    }

    private String normalizeVendor(String vendor) {
        if (vendor == null || vendor.isBlank()) {
            return llmProperties.defaultVendor().getValue();
        }
        return vendor.trim();
    }

    private String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        return model.trim();
    }
}
