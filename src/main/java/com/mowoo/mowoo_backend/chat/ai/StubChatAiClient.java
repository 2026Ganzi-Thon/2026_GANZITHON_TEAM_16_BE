package com.mowoo.mowoo_backend.chat.ai;

import org.springframework.stereotype.Component;

// 실제 AI를 연결하기 전까지 쓰는 임시 구현. 항상 같은 형식의 응답을 돌려준다.
// 실제 구현을 추가하면 이 클래스를 지운다.
@Component
public class StubChatAiClient implements ChatAiClient {

    @Override
    public String reply(ChatAiRequest request) {
        return request.characterName() + "가 듣고 있어요. 조금 더 이야기해 줄래요?";
    }
}
