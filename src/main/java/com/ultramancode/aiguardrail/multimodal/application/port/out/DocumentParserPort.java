package com.ultramancode.aiguardrail.multimodal.application.port.out;

public interface DocumentParserPort {
    /**
     * 문서 바이너리에서 텍스트 추출
     */
    String extractText(byte[] documentBytes);
}
