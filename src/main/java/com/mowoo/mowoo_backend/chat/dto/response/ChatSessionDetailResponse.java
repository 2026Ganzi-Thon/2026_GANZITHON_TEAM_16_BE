package com.mowoo.mowoo_backend.chat.dto.response;

import com.mowoo.mowoo_backend.global.response.CursorResponse;

// GET /api/v1/chat-sessions/{sessionId}
public record ChatSessionDetailResponse(
        ChatSessionResponse session,
        CursorResponse<ChatMessageResponse> messages
) {
}
