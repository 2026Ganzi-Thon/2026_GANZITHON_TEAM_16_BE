package com.mowoo.mowoo_backend.chat.ai;

// 대화 기록을 받아 캐릭터의 다음 응답을 만든다.
// 응답을 만들지 못하면 ChatAiException을 던지고, 서비스가 AI_UNAVAILABLE로 바꿔 응답한다.
public interface ChatAiClient {

    String reply(ChatAiRequest request);
}
