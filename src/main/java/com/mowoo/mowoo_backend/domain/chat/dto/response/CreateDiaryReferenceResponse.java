package com.mowoo.mowoo_backend.domain.chat.dto.response;

// POST /api/v1/chat-sessions/{sessionId}/diary-references
public record CreateDiaryReferenceResponse(
        ChatMessageResponse assistantMessage
) {
}
