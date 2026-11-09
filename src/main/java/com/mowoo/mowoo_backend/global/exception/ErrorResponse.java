package com.mowoo.mowoo_backend.global.exception;

import com.mowoo.mowoo_backend.global.code.BaseCode;

public record ErrorResponse(boolean isSuccess, String message, Detail error) {

    public record Detail(String code, String message) {
    }

    public static ErrorResponse of(BaseCode errorCode) {
        String message = errorCode.getMessage();
        return new ErrorResponse(false, message, new Detail(errorCode.name(), message));
    }
}
