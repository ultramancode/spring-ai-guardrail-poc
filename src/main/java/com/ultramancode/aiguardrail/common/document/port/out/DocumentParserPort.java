package com.ultramancode.aiguardrail.common.document.port.out;

public interface DocumentParserPort {
    /**
     * 문서 바이너리에서 텍스트를 추출합니다.
     */
    String extractText(byte[] documentBytes);
}
