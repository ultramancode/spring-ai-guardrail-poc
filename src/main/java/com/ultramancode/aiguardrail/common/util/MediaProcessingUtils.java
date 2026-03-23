package com.ultramancode.aiguardrail.common.util;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Consumer;
import java.util.function.Function;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MediaProcessingUtils {

    public static PdfProcessingResult extractMaskedPdfText(
            byte[] fileBytes,
            String onPdfExtractFail,
            Function<byte[], String> textExtractor,
            Function<String, String> tokenizeFunction,
            Consumer<String> warnLogger
    ) {
        String extractedText = PdfExtractPolicy.extractTextOrNotice(
                onPdfExtractFail,
                () -> textExtractor.apply(fileBytes),
                warnLogger
        );
        String maskedExtractedText = tokenizeFunction.apply(extractedText);
        return new PdfProcessingResult(extractedText, maskedExtractedText);
    }

    public static ImagePayload prepareImagePayload(AttachmentFile file) {
        if (file == null) {
            throw new IllegalArgumentException("image file must not be null.");
        }

        String normalizedContentType = MediaTypePolicy.validateImageOrThrow(file.getContentType());
        byte[] fileBytes = file.getBytes();
        return new ImagePayload(fileBytes, normalizedContentType);
    }

    public record PdfProcessingResult(String extractedText, String maskedExtractedText) {
    }

    public record ImagePayload(byte[] bytes, String contentType) {
    }
}
