package com.mowoo.mowoo_backend.chat.dto.response;

import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;

// GET /api/v1/chat-sessions/{sessionId}
public record ChatSessionDetailResponse(
        boolean isSuccess,
        String code,
        String message,
        ChatSessionItem session,
        ChatMessagePage messages
) {

    public ChatSessionDetailResponse(ChatSessionItem session, ChatMessagePage messages) {
        this(true, CommonSuccessCode.CHAT_SESSION_READ_SUCCESS.name(),
                CommonSuccessCode.CHAT_SESSION_READ_SUCCESS.getMessage(), session, messages);
    }
}
