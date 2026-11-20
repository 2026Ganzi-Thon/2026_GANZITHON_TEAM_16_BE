package com.mowoo.mowoo_backend.chat.ai;

import java.time.LocalDate;
import java.util.List;

// 사용자의 현재 고민 메시지와 관련된 과거 일기를 후보 중에서 고른다.
// 고르지 못하면 ChatAiException을 던지고, 서비스는 빈 후보 목록을 돌려준다.
public interface DiarySuggestionAiClient {

    // 관련 있는 후보의 candidates 내 위치(0부터)를 관련도 높은 순으로 최대 limit개 돌려준다. 없으면 빈 목록이다.
    List<Integer> select(String concern, List<Candidate> candidates, int limit);

    record Candidate(LocalDate entryDate, String title, String content) {
    }
}
