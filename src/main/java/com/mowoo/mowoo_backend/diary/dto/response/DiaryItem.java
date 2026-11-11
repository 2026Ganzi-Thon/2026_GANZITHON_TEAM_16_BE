package com.mowoo.mowoo_backend.diary.dto.response;


import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 목록 내부에는 최상위 공통 성공 필드를 반복하지 않는다.
public record DiaryItem(
        UUID id,
        UUID sourceSessionId,
        LocalDate entryDate,
        String title,
        String content,
        Instant savedAt,
        Instant emotionConfirmedAt,
        List<DiaryResponse.Emotion> emotions,
        List<DiaryResponse.Attachment> attachments
) {

}
