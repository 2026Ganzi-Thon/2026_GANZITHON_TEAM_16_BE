package com.mowoo.mowoo_backend.calendar.dto;

import com.mowoo.mowoo_backend.emotion.dto.EmotionTypeResponse;
import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CalendarResponse(boolean isSuccess, String code, String message, String month, List<Day> days) {
    public CalendarResponse(String month, List<Day> days) {
        this(true, CommonSuccessCode.SUCCESS.name(), CommonSuccessCode.SUCCESS.getMessage(), month, days);
    }
    public record Day(LocalDate date, EmotionTypeResponse primaryEmotion, List<UUID> diaryIds) {
    }
}
