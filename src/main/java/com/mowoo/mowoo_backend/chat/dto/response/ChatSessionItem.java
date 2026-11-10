package com.mowoo.mowoo_backend.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

// 대화 세션 데이터. 보관함 목록의 항목과 대화 상세의 session에 쓴다.
// 대화 시작·종료 응답은 같은 필드를 최상위에 펼친 ChatSessionResponse로 반환한다.
public record ChatSessionItem(
        UUID id,
        UUID characterId,
        String title,
        String summary,
        Instant startedAt,
        Instant endedAt,
        int completedTurns,
        boolean canGenerateDiary
) {
}
