package com.ultramancode.aiguardrail.multimodal.infrastructure.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * PDF 바이너리에서 텍스트를 추출한다.
 */
@Slf4j
@Component
public class PdfTextExtractor {

    /**
     * PDF 본문 텍스트를 추출한다.
     */
    public String extractText(byte[] documentBytes) {
        if (documentBytes == null || documentBytes.length == 0) {
            return "";
        }

        try (PDDocument document = Loader.loadPDF(documentBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.debug("[PDF] Extracted {} characters", text != null ? text.length() : 0);
            return text != null ? text.trim() : "";
        } catch (IOException e) {
            log.error("[PDF] Failed to extract text from PDF", e);
            throw new IllegalStateException("PDF text extraction failed", e);
        }
    }
}
