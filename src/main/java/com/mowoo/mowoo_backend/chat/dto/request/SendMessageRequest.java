package com.mowoo.mowoo_backend.chat.dto.request;

import java.util.UUID;

// POST /api/v1/chat-sessions/{sessionId}/messages
// 본문이 비어 있으면 MESSAGE_EMPTY로 거절한다.
// characterId가 없으면 대화 세션의 캐릭터로 응답한다.
public record SendMessageRequest(
        String content,
        UUID characterId
) {

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }
}
