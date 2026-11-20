package com.mowoo.mowoo_backend.calendar.dto;

import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import java.util.List;
import java.util.UUID;

public record CalendarRecapResponse(boolean isSuccess, String code, String message, String month,
                                    List<EmotionCount> emotionCounts, String concernSummary) {
    public CalendarRecapResponse(String month, List<EmotionCount> counts, String summary) {
        this(true, CommonSuccessCode.SUCCESS.name(), CommonSuccessCode.SUCCESS.getMessage(), month, counts, summary);
    }
    public record EmotionCount(UUID emotionTypeId, long count) { }
}
