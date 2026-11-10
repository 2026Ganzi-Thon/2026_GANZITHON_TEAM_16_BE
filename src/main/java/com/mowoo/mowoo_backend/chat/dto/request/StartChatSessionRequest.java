package com.mowoo.mowoo_backend.chat.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// POST /api/v1/chat-sessions
public record StartChatSessionRequest(
        @NotNull UUID characterId
) {
}
