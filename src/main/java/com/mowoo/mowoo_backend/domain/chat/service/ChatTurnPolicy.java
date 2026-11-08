package com.mowoo.mowoo_backend.domain.chat.service;

import com.mowoo.mowoo_backend.domain.chat.entity.SenderRole;

import java.util.List;

// 대화 왕복 수와 AI 일기 생성 가능 여부를 계산한다. 일기 파트의 AI 초안 생성(MIN_CHAT_TURNS)도 같은 기준을 쓴다.
public final class ChatTurnPolicy {

    public static final int MIN_TURNS_FOR_DIARY = 5;

    private ChatTurnPolicy() {
    }

    // 사용자 메시지 바로 다음에 AI 응답이 저장된 쌍을 한 왕복으로 센다.
    // AI 응답 없이 남은 사용자 메시지나, 과거 일기 비교처럼 사용자 메시지 없이 추가된 AI 응답은 세지 않는다.
    public static int countCompletedTurns(List<SenderRole> senderRolesInSequence) {
        int completedTurns = 0;

        for (int i = 1; i < senderRolesInSequence.size(); i++) {
            if (senderRolesInSequence.get(i - 1) == SenderRole.USER
                    && senderRolesInSequence.get(i) == SenderRole.ASSISTANT) {
                completedTurns++;
            }
        }

        return completedTurns;
    }

    public static boolean canGenerateDiary(int completedTurns) {
        return completedTurns >= MIN_TURNS_FOR_DIARY;
    }
}
