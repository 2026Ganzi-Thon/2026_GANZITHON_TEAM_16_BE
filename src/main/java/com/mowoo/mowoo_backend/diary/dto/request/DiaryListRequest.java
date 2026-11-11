package com.mowoo.mowoo_backend.diary.dto.request;

import com.mowoo.mowoo_backend.global.request.CursorRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

public record DiaryListRequest(
        LocalDate from,
        LocalDate to,
        @Min(1) @Max(100) Integer limit,
        String cursor
) {

    public DiaryListRequest {
        if (limit == null) {
            limit = CursorRequest.DEFAULT_LIMIT;
        }
    }
}
