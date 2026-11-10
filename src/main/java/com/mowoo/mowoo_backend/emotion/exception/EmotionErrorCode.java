package com.mowoo.mowoo_backend.emotion.exception;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EmotionErrorCode implements BaseCode {
    INVALID_EMOTIONS(HttpStatus.BAD_REQUEST, "선택한 감정을 확인해 주세요."),
    DIARY_NOT_SAVED(HttpStatus.CONFLICT, "일기를 저장한 뒤 감정을 선택해 주세요."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 응답을 잠시 후 다시 요청해 주세요.");

    private final HttpStatus status;
    private final String message;
}
