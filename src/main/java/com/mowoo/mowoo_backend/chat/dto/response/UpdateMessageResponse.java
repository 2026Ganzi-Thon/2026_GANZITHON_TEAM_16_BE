package com.mowoo.mowoo_backend.chat.dto.response;

import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;

// PATCH /api/v1/chat-sessions/{sessionId}/messages/{messageId}
public record UpdateMessageResponse(
        boolean isSuccess,
        String code,
        String message,
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage
) {

    public UpdateMessageResponse(ChatMessageResponse userMessage, ChatMessageResponse assistantMessage) {
        this(true, CommonSuccessCode.CHAT_MESSAGE_UPDATE_SUCCESS.name(),
                CommonSuccessCode.CHAT_MESSAGE_UPDATE_SUCCESS.getMessage(), userMessage, assistantMessage);
    }
}
