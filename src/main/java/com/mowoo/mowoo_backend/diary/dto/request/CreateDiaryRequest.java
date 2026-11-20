package com.mowoo.mowoo_backend.diary.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;

public record CreateDiaryRequest(
        @NotNull LocalDate entryDate,
        @NotBlank @Size(max = 150) String title,
        @NotBlank String content
) {

    // TEXT의 한도는 글자 수가 아닌 바이트 수다. 한글과 이모지도 저장 전에 검증한다.
    @AssertTrue
    @JsonIgnore
    public boolean isContentWithinStorageLimit() {
        return content == null || content.getBytes(StandardCharsets.UTF_8).length <= 65535;
    }

    @AssertTrue
    @JsonIgnore
    public boolean isEntryDateWithinStorageRange() {
        return entryDate == null || (entryDate.getYear() >= 1000 && entryDate.getYear() <= 9999);
    }
}
