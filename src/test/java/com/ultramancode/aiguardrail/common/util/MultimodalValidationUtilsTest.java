package com.ultramancode.aiguardrail.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultimodalValidationUtilsTest {

    @Test
    void normalizeAndValidateText_returnsTrimmedText_whenInputIsValid() {
        String normalized = MultimodalValidationUtils.normalizeAndValidateText("  hello world  ", "text", 100);

        assertEquals("hello world", normalized);
    }

    @Test
    void normalizeAndValidateText_throwsException_whenInputIsBlank() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MultimodalValidationUtils.normalizeAndValidateText("   ", "text", 100)
        );

        assertEquals("text must not be blank.", exception.getMessage());
    }

    @Test
    void validateMultipartFile_throwsException_whenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MultimodalValidationUtils.validateMultipartFile(emptyFile, 1024, "multimodal")
        );

        assertEquals("multimodal file is empty.", exception.getMessage());
    }

    @Test
    void validateFileSize_throwsException_whenFileExceedsLimit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MultimodalValidationUtils.validateFileSize(2048, "file.pdf", 1024, "pdf")
        );

        assertEquals(
                "pdf file size exceeds limit. file=file.pdf, size=2048, max=1024",
                exception.getMessage()
        );
    }

    @Test
    void truncateText_returnsTruncatedText_whenLengthExceedsMaxChars() {
        String truncated = MultimodalValidationUtils.truncateText(
                "1234567890",
                5,
                "content",
                message -> {
                }
        );

        assertEquals("12345", truncated);
    }
}
