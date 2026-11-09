package com.mowoo.mowoo_backend.chat.ai;

import com.mowoo.mowoo_backend.chat.entity.SenderRole;

import java.util.List;

// messages는 순번 순서이고 마지막 항목이 이번에 답할 사용자 메시지다.
public record ChatAiRequest(
        String characterName,
        String characterDescription,
        List<Message> messages
) {

    // 사진만 보낸 메시지는 content가 null일 수 있다.
    public record Message(SenderRole senderRole, String content) {
    }
}
