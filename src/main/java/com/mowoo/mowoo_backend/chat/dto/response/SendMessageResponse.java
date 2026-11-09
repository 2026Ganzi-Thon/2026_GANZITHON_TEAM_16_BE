package com.mowoo.mowoo_backend.chat.dto.response;

// POST /api/v1/chat-sessions/{sessionId}/messages
public record SendMessageResponse(
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage,
        int completedTurns,
        boolean canGenerateDiary
) {
}
