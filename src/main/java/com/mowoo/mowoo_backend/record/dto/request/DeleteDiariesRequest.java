package com.mowoo.mowoo_backend.record.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record DeleteDiariesRequest(
        @Schema(description = "삭제할 본인 일기 ID 목록. 중복을 포함해 요청 배열 기준 1~100개입니다.")
        @NotEmpty @Size(max = 100, message = "한 번에 삭제할 일기는 최대 100개입니다.") List<@NotNull UUID> diaryIds
) {
}
