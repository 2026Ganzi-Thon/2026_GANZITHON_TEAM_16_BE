package com.mowoo.mowoo_backend.chat.dto.response;

import com.mowoo.mowoo_backend.chat.entity.SenderRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 사용자 메시지는 characterId가 null이다.
public record ChatMessageResponse(
        UUID id,
        int sequenceNo,
        SenderRole senderRole,
        UUID characterId,
        String content,
        Instant createdAt,
        Instant editedAt,
        List<DiaryReference> diaryReferences
) {

    public record DiaryReference(
            UUID diaryId,
            LocalDate entryDate,
            String title,
            Instant acceptedAt
    ) {
    }
}
