package com.mowoo.mowoo_backend.emotion.dto;

import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import java.util.List;
import java.util.UUID;

public record EmotionSuggestionResponse(boolean isSuccess, String code, String message,
                                        List<UUID> suggestedEmotionIds, UUID primaryEmotionId) {
    public EmotionSuggestionResponse(List<UUID> ids, UUID primary) {
        this(true, CommonSuccessCode.SUCCESS.name(), CommonSuccessCode.SUCCESS.getMessage(), List.copyOf(ids), primary);
    }
}
