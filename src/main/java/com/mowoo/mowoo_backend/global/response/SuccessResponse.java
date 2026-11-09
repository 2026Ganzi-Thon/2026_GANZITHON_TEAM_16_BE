package com.mowoo.mowoo_backend.global.response;

import com.mowoo.mowoo_backend.global.code.BaseCode;

public record SuccessResponse(boolean isSuccess, String code, String message) {

    public static SuccessResponse of(BaseCode code) {
        return new SuccessResponse(true, code.name(), code.getMessage());
    }
}
