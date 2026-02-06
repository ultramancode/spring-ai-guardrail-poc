package com.ultramancode.aiguardrail.multimodal.infrastructure.adapter;

import com.ultramancode.aiguardrail.multimodal.application.port.out.DocumentParserPort;
import com.ultramancode.aiguardrail.multimodal.infrastructure.document.PdfTextExtractor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PdfDocumentParserAdapter implements DocumentParserPort {

    private final PdfTextExtractor pdfTextExtractor;

    @Override
    public String extractText(byte[] documentBytes) {
        return pdfTextExtractor.extractText(documentBytes);
    }
}
