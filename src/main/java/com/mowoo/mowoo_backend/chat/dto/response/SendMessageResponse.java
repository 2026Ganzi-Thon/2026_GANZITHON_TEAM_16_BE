package com.mowoo.mowoo_backend.chat.dto.response;

import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;

// POST /api/v1/chat-sessions/{sessionId}/messages
public record SendMessageResponse(
        boolean isSuccess,
        String code,
        String message,
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage,
        int completedTurns,
        boolean canGenerateDiary
) {

    public SendMessageResponse(
            ChatMessageResponse userMessage,
            ChatMessageResponse assistantMessage,
            int completedTurns,
            boolean canGenerateDiary
    ) {
        this(true, CommonSuccessCode.CHAT_MESSAGE_SEND_SUCCESS.name(),
                CommonSuccessCode.CHAT_MESSAGE_SEND_SUCCESS.getMessage(),
                userMessage, assistantMessage, completedTurns, canGenerateDiary);
    }
}
