package com.ultramancode.aiguardrail.common.llm;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LlmConstants {

    public static final String VENDOR_GOOGLE = "google";
    public static final String VENDOR_OLLAMA = "ollama";

    public static final String MEDIA_TYPE_PDF = "application/pdf";
    public static final String MEDIA_TYPE_IMAGE_PREFIX = "image/";
}
