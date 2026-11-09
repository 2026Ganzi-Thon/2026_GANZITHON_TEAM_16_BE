package com.mowoo.mowoo_backend.chat.dto.response;

// PATCH /api/v1/chat-sessions/{sessionId}/messages/{messageId}
public record UpdateMessageResponse(
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage
) {
}
