package com.mowoo.mowoo_backend.chat.dto.request;

import jakarta.validation.constraints.NotNull;

// PATCH /api/v1/chat-sessions/{sessionId}
public record UpdateChatSessionRequest(
        @NotNull ChatSessionAction action
) {
}
