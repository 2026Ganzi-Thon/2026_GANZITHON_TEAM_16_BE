package com.mowoo.mowoo_backend.emotion.dto;

import com.mowoo.mowoo_backend.emotion.entity.EmotionType;
import java.util.UUID;

public record EmotionTypeResponse(UUID id, String code, String name, String colorHex, String iconUrl) {
    public static EmotionTypeResponse from(EmotionType emotion) {
        return new EmotionTypeResponse(emotion.getId(), emotion.getCode(), emotion.getName(),
                emotion.getColorHex(), emotion.getIconUrl());
    }
}
