package com.mowoo.mowoo_backend.record.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record DeleteDiariesRequest(@NotEmpty List<@NotNull UUID> diaryIds) {
}
