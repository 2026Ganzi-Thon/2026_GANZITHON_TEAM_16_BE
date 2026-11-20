package com.mowoo.mowoo_backend.chat.dto.response;

import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;

// POST /api/v1/chat-sessions/{sessionId}/diary-references
public record CreateDiaryReferenceResponse(
        boolean isSuccess,
        String code,
        String message,
        ChatMessageResponse assistantMessage
) {

    public CreateDiaryReferenceResponse(ChatMessageResponse assistantMessage) {
        this(true, CommonSuccessCode.DIARY_REFERENCE_CREATE_SUCCESS.name(),
                CommonSuccessCode.DIARY_REFERENCE_CREATE_SUCCESS.getMessage(), assistantMessage);
    }
}
