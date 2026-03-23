package com.ultramancode.aiguardrail.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PromptTemplateSanitizer {

    public static String sanitize(String prompt, String placeholderKey) {
        if (prompt == null) {
            return "";
        }
        if (placeholderKey == null || placeholderKey.isBlank()) {
            return prompt;
        }

        return prompt.replace("{" + placeholderKey + "}", "[INPUT_IN_USER_MESSAGE]");
    }
}
