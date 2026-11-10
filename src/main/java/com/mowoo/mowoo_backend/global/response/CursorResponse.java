package com.mowoo.mowoo_backend.global.response;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import java.util.List;

// 마지막 페이지면 nextCursor는 null이다.
public record CursorResponse<T>(boolean isSuccess, String code, String message, List<T> items, String nextCursor) {

    public CursorResponse(List<T> items, String nextCursor) {
        this(CommonSuccessCode.SUCCESS, items, nextCursor);
    }

    public CursorResponse(BaseCode code, List<T> items, String nextCursor) {
        this(true, code.name(), code.getMessage(), items, nextCursor);
    }
}
