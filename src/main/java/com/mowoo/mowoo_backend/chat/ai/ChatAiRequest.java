package com.mowoo.mowoo_backend.chat.ai;

import com.mowoo.mowoo_backend.chat.entity.SenderRole;

import java.util.List;

// characterCode는 캐릭터별 말투 가이드(ChatPrompts.persona)를 찾는 데 쓴다.
// messages는 순번 순서이고 마지막 항목이 이번에 답할 사용자 메시지다.
public record ChatAiRequest(
        String characterCode,
        String characterName,
        String characterDescription,
        List<Message> messages
) {

    public record Message(SenderRole senderRole, String content) {
    }
}
