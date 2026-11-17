package com.mowoo.mowoo_backend.chat.dto.response;

import java.time.LocalDate;
import java.util.UUID;

// GET /api/v1/chat-sessions/{sessionId}/diary-suggestions 의 항목
// invitation은 이 일기를 함께 살펴보자고 권하는 캐릭터의 말이다. 대화 기록에는 저장하지 않는다.
public record DiarySuggestionResponse(
        UUID diaryId,
        LocalDate entryDate,
        String title,
        String invitation
) {
}
