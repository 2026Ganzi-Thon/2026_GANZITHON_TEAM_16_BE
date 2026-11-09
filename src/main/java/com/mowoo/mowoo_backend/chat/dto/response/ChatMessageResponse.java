package com.mowoo.mowoo_backend.chat.dto.response;

import com.mowoo.mowoo_backend.chat.entity.SenderRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 사용자 메시지는 characterId가 null이다. 사진만 보낸 메시지는 content가 null일 수 있다.
public record ChatMessageResponse(
        UUID id,
        int sequenceNo,
        SenderRole senderRole,
        UUID characterId,
        String content,
        Instant createdAt,
        Instant editedAt,
        List<Attachment> attachments,
        List<DiaryReference> diaryReferences
) {

    // url은 접근 확인 후 발급하는 만료 URL이다.
    public record Attachment(
            UUID id,
            String mimeType,
            String url,
            int sortOrder
    ) {
    }

    public record DiaryReference(
            UUID diaryId,
            LocalDate entryDate,
            String title,
            Instant acceptedAt
    ) {
    }
}
