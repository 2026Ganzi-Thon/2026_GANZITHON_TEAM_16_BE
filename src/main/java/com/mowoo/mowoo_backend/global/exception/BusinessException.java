package com.mowoo.mowoo_backend.global.exception;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final BaseCode errorCode;

    public BusinessException(BaseCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
