package com.mowoo.mowoo_backend.attachment.exception;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AttachmentErrorCode implements BaseCode {
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "정상적인 JPEG 또는 PNG 사진을 첨부해 주세요."),
    TOO_MANY_ATTACHMENTS(HttpStatus.BAD_REQUEST, "사진은 기록당 최대 3장까지 첨부할 수 있어요."),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "첨부 사진을 찾을 수 없어요."),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "사진 저장소를 사용할 수 없어요. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
