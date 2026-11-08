package com.mowoo.mowoo_backend.domain.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

// 대화 시작·종료 응답, 보관함 목록의 항목, 대화 상세의 session
public record ChatSessionResponse(
        UUID id,
        UUID characterId,
        String title,
        String summary,
        Instant startedAt,
        Instant endedAt,
        int completedTurns,
        boolean canGenerateDiary
) {
}
