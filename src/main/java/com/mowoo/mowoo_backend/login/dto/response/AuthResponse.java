package com.mowoo.mowoo_backend.login.dto.response;

import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;

import java.util.UUID;

public record AuthResponse(boolean isSuccess, String code, String message, UUID id, String username, String accessToken) {

    public AuthResponse(UUID id, String username, String accessToken) {
        this(true, CommonSuccessCode.LOGIN_SUCCESS.name(), CommonSuccessCode.LOGIN_SUCCESS.getMessage(), id, username, accessToken);
    }
}
