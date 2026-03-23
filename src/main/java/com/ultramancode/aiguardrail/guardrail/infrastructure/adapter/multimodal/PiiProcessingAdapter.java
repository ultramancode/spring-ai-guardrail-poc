package com.ultramancode.aiguardrail.guardrail.infrastructure.adapter.multimodal;

import com.ultramancode.aiguardrail.common.pii.port.out.PiiProcessingPort;
import com.ultramancode.aiguardrail.guardrail.application.port.in.PiiUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PiiProcessingPort의 구현체로, guardrail 모듈의 PiiUseCase를 호출하여 처리합니다.
 */
@Component
@RequiredArgsConstructor
public class PiiProcessingAdapter implements PiiProcessingPort {

    private final PiiUseCase piiService;

    @Override
    public String tokenize(String text) {
        return piiService.tokenize(text);
    }

    @Override
    public String detokenize(String text) {
        return piiService.detokenize(text);
    }
}
