package com.ultramancode.aiguardrail.guardrail.application.port.in;

public interface PiiUseCase {
    String tokenize(String text);

    String tokenizeInternal(String text);

    String detokenize(String text);

    String detokenizeInternal(String text);

    Object detokenizeRec(Object input);
}
