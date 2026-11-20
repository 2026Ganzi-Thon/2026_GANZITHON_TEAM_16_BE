package com.mowoo.mowoo_backend.calendar.exception;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CalendarErrorCode implements BaseCode {
    INVALID_MONTH(HttpStatus.BAD_REQUEST, "조회할 월을 확인해 주세요."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 응답을 잠시 후 다시 요청해 주세요.");
    private final HttpStatus status;
    private final String message;
}
