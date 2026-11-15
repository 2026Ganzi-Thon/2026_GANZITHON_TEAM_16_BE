package com.mowoo.mowoo_backend.global.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;

// 커서 페이지 목록 조회의 쿼리 파라미터. limit은 기본 20, 최대 100이다.
public record CursorRequest(
        @Schema(description = "한 페이지 항목 수", defaultValue = "20", minimum = "1", maximum = "100")
        @Min(1) @Max(100) Integer limit,
        @Schema(description = "이전 응답의 nextCursor. 첫 페이지에서는 생략합니다.") String cursor
) {

    public static final int DEFAULT_LIMIT = 20;

    public CursorRequest {
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
    }
}
