package com.mowoo.mowoo_backend.domain.chat.dto.response;

import java.time.LocalDate;
import java.util.UUID;

// GET /api/v1/chat-sessions/{sessionId}/diary-suggestions 의 항목
public record DiarySuggestionResponse(
        UUID diaryId,
        LocalDate entryDate,
        String title
) {
}
