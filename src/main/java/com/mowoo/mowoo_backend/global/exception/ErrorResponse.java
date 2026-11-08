package com.mowoo.mowoo_backend.global.exception;

// API 명세 공통 규약의 에러 응답 형식: {"error": {"code": "...", "message": "..."}}
public record ErrorResponse(Detail error) {

    public record Detail(String code, String message) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(new Detail(errorCode.name(), errorCode.getMessage()));
    }
}
