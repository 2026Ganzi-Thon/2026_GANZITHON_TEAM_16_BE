package com.mowoo.mowoo_backend.global.response;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonSuccessCode implements BaseCode {

    SUCCESS(HttpStatus.OK, "요청이 처리됐어요."),
    HEALTH_CHECK_SUCCESS(HttpStatus.OK, "OK");

    private final HttpStatus status;
    private final String message;
}
