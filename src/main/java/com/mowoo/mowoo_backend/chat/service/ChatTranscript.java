package com.mowoo.mowoo_backend.chat.service;

import com.mowoo.mowoo_backend.chat.entity.SenderRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// AI 일기 초안 생성에 넘길 대화 기록. ChatService.getTranscript로 읽는다.
// canGenerateDiary가 false이면 일기 쪽에서 MIN_CHAT_TURNS로 거절한다.
public record ChatTranscript(
        UUID sessionId,
        Instant startedAt,
        Instant endedAt,
        int completedTurns,
        boolean canGenerateDiary,
        List<Message> messages
) {

    // messages는 순번 순서다. 사용자 메시지는 characterName이 null이다.
    public record Message(
            int sequenceNo,
            SenderRole senderRole,
            String characterName,
            String content,
            Instant createdAt
    ) {
    }
}
