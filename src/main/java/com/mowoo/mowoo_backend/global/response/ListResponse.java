package com.mowoo.mowoo_backend.global.response;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import java.util.List;

public record ListResponse<T>(boolean isSuccess, String code, String message, List<T> items) {

    public ListResponse(List<T> items) {
        this(CommonSuccessCode.SUCCESS, items);
    }

    public ListResponse(BaseCode code, List<T> items) {
        this(true, code.name(), code.getMessage(), items);
    }
}
