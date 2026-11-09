package com.mowoo.mowoo_backend.login.exception;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseCode {
    SOCIAL_CODE_INVALID(HttpStatus.BAD_REQUEST, "소셜 로그인 인가 코드가 유효하지 않아요."),
    SOCIAL_IDENTITY_CONFLICT(HttpStatus.CONFLICT, "소셜 계정 연결이 충돌했어요. 다시 로그인해 주세요."),
    SOCIAL_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "카카오 로그인에 연결할 수 없어요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인 세션이 만료됐어요. 다시 로그인해 주세요.");

    private final HttpStatus status;
    private final String message;
}
