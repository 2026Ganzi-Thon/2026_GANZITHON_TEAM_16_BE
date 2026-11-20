package com.mowoo.mowoo_backend.chat.ai;

import java.util.List;

// 종료한 대화의 기록으로 보관함용 제목과 요약을 만든다.
// 만들지 못하면 ChatAiException을 던지고, 서비스는 제목과 요약을 null로 두고 종료한다.
public interface ChatSummaryAiClient {

    // messages는 순번 순서다.
    Summary summarize(List<ChatAiRequest.Message> messages);

    record Summary(String title, String summary) {
    }
}
