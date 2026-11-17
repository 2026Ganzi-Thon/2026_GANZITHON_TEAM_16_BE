package com.mowoo.mowoo_backend.chat.ai;

import java.time.LocalDate;
import java.util.List;

// 사용자의 현재 고민 메시지와 관련된 과거 일기를 후보 중에서 고르고, 고른 일기마다 캐릭터의 권유 문장을 만든다.
// 고르지 못하면 ChatAiException을 던지고, 서비스는 빈 후보 목록을 돌려준다.
public interface DiarySuggestionAiClient {

    // 관련 있는 후보를 관련도 높은 순으로 최대 limit개 돌려준다. 없으면 빈 목록이다.
    List<Suggestion> select(Request request, int limit);

    record Request(
            String characterName,
            String characterDescription,
            String concern,
            List<Candidate> candidates
    ) {
    }

    record Candidate(LocalDate entryDate, String title, String content) {
    }

    // index는 candidates 내 위치(0부터)이고, invitation은 그 일기를 함께 살펴보자고 권하는 캐릭터의 말이다.
    record Suggestion(int index, String invitation) {
    }
}
