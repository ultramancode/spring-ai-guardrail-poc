package com.ultramancode.aiguardrail.common.file;

import lombok.Getter;

/**
 * 프레임워크 타입과 분리된 업로드 파일 표현 객체.
 */
@Getter
public class AttachmentFile {

    private final String originalFilename;
    private final String contentType;
    private final byte[] bytes;

    public AttachmentFile(String originalFilename, String contentType, byte[] bytes) {
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        if (bytes == null) {
            this.bytes = new byte[0];
        } else {
            this.bytes = bytes.clone();
        }
    }

    public boolean isEmpty() {
        if (bytes.length == 0) {
            return true;
        }
        return false;
    }

    public byte[] getBytes() {
        return bytes.clone();
    }
}
