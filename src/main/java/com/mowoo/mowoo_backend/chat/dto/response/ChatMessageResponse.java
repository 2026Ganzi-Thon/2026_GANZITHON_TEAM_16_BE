package com.mowoo.mowoo_backend.chat.dto.response;

import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import com.mowoo.mowoo_backend.attachment.service.AttachmentService;

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
        List<DiaryReference> diaryReferences,
        List<AttachmentService.View> attachments
) {
    public ChatMessageResponse(UUID id, int sequenceNo, SenderRole senderRole, UUID characterId, String content,
                               Instant createdAt, Instant editedAt, List<DiaryReference> references) {
        this(id, sequenceNo, senderRole, characterId, content, createdAt, editedAt, references, List.of());
    }

    public record DiaryReference(
            UUID diaryId,
            LocalDate entryDate,
            String title,
            Instant acceptedAt
    ) {
    }
}
