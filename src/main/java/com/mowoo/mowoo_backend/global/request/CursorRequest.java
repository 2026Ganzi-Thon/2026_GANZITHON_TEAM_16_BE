package com.mowoo.mowoo_backend.global.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 커서 페이지 목록 조회의 쿼리 파라미터. limit은 기본 20, 최대 100이다.
public record CursorRequest(
        @Min(1) @Max(100) Integer limit,
        String cursor
) {

    public static final int DEFAULT_LIMIT = 20;

    public CursorRequest {
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
    }
}
