package com.mowoo.mowoo_backend.chat.ai;

import java.time.LocalDate;

// 사용자의 현재 고민과 연결에 동의한 과거 일기를 비교하는 캐릭터의 답변을 만든다.
// 만들지 못하면 ChatAiException을 던지고, 서비스가 AI_UNAVAILABLE로 바꿔 응답한다.
public interface DiaryComparisonAiClient {

    String compare(Request request);

    record Request(
            String characterName,
            String characterDescription,
            String concern,
            LocalDate diaryEntryDate,
            String diaryTitle,
            String diaryContent
    ) {
    }
}
