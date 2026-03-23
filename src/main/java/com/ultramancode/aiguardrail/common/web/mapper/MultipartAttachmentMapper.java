package com.ultramancode.aiguardrail.common.web.mapper;

import com.ultramancode.aiguardrail.common.file.AttachmentFile;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 웹 계층 MultipartFile을 애플리케이션 공통 파일 객체로 변환하는 유틸리티.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MultipartAttachmentMapper {

    @Nullable
    public static AttachmentFile fromMultipartFile(@Nullable MultipartFile multipartFile) {
        if (multipartFile == null) {
            return null;
        }

        try {
            return new AttachmentFile(
                    multipartFile.getOriginalFilename(),
                    multipartFile.getContentType(),
                    multipartFile.getBytes()
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file.", e);
        }
    }
}
