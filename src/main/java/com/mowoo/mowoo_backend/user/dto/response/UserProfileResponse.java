package com.mowoo.mowoo_backend.user.dto.response;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import com.mowoo.mowoo_backend.user.entity.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserProfileResponse(
        boolean isSuccess,
        String code,
        String message,
        UUID id,
        String nickname,
        String email,
        String profileImageUrl,
        LocalDate birthDate,
        Instant createdAt,
        boolean usePastDiaries,
        boolean eveningReminderEnabled
) {
    public static UserProfileResponse from(User user, BaseCode code) {
        return new UserProfileResponse(true, code.name(), code.getMessage(), user.getId(), user.getNickname(),
                user.getEmail(), user.getProfileImageUrl(), user.getBirthDate(), user.getCreatedAt(),
                user.isUsePastDiaries(), user.isEveningReminderEnabled());
    }
}
