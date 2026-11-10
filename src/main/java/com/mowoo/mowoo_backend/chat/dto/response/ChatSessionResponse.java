package com.mowoo.mowoo_backend.chat.dto.response;

import com.mowoo.mowoo_backend.global.code.BaseCode;

import java.time.Instant;
import java.util.UUID;

// 대화 시작·종료 응답
public record ChatSessionResponse(
        boolean isSuccess,
        String code,
        String message,
        UUID id,
        UUID characterId,
        String title,
        String summary,
        Instant startedAt,
        Instant endedAt,
        int completedTurns,
        boolean canGenerateDiary
) {

    public static ChatSessionResponse of(BaseCode code, ChatSessionItem session) {
        return new ChatSessionResponse(true, code.name(), code.getMessage(), session.id(), session.characterId(),
                session.title(), session.summary(), session.startedAt(), session.endedAt(),
                session.completedTurns(), session.canGenerateDiary());
    }
}
