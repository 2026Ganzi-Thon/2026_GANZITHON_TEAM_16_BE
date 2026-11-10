package com.mowoo.mowoo_backend.chat.dto.request;

import jakarta.validation.constraints.NotBlank;

// PATCH /api/v1/chat-sessions/{sessionId}/messages/{messageId}
public record UpdateMessageRequest(
        @NotBlank String content
) {
}
