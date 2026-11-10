package com.mowoo.mowoo_backend.diary.dto.response;

import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DiaryResponse(
        boolean isSuccess,
        String code,
        String message,
        UUID id,
        UUID sourceSessionId,
        LocalDate entryDate,
        String title,
        String content,
        Instant savedAt,
        Instant emotionConfirmedAt,
        List<Emotion> emotions,
        List<Attachment> attachments
) {

    // 감정·첨부 구현 전에도 기존 상세 응답의 배열 필드를 유지한다.
    public static DiaryResponse from(DiaryItem item) {
        CommonSuccessCode success = CommonSuccessCode.SUCCESS;
        return new DiaryResponse(true, success.name(), success.getMessage(), item.id(), item.sourceSessionId(),
                item.entryDate(), item.title(), item.content(), item.savedAt(), item.emotionConfirmedAt(),
                item.emotions(), item.attachments());
    }

    public record Emotion(UUID id, String code, String name, String colorHex, String iconUrl, boolean isPrimary) {
    }

    public record Attachment(UUID id, String mimeType, String url, int sortOrder) {
    }
}
