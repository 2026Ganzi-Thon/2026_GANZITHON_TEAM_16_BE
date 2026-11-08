package com.mowoo.mowoo_backend.domain.chat.service;

import com.mowoo.mowoo_backend.domain.chat.entity.SenderRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.mowoo.mowoo_backend.domain.chat.entity.SenderRole.ASSISTANT;
import static com.mowoo.mowoo_backend.domain.chat.entity.SenderRole.USER;
import static org.assertj.core.api.Assertions.assertThat;

class ChatTurnPolicyTest {

    @Test
    void newSessionHasNoTurns() {
        assertThat(ChatTurnPolicy.countCompletedTurns(List.of())).isZero();
    }

    @Test
    void userMessageFollowedByReplyIsOneTurn() {
        assertThat(ChatTurnPolicy.countCompletedTurns(List.of(USER, ASSISTANT))).isEqualTo(1);
    }

    @Test
    void userMessageWithoutReplyIsNotCounted() {
        assertThat(ChatTurnPolicy.countCompletedTurns(List.of(USER, ASSISTANT, USER))).isEqualTo(1);
        assertThat(ChatTurnPolicy.countCompletedTurns(List.of(USER, USER, ASSISTANT))).isEqualTo(1);
    }

    @Test
    void extraReplyFromPastDiaryComparisonIsNotCounted() {
        assertThat(ChatTurnPolicy.countCompletedTurns(List.of(USER, ASSISTANT, ASSISTANT))).isEqualTo(1);
    }

    @Test
    void diaryCanBeGeneratedFromFiveTurns() {
        assertThat(ChatTurnPolicy.canGenerateDiary(ChatTurnPolicy.countCompletedTurns(turns(4)))).isFalse();
        assertThat(ChatTurnPolicy.canGenerateDiary(ChatTurnPolicy.countCompletedTurns(turns(5)))).isTrue();
    }

    private List<SenderRole> turns(int count) {
        List<SenderRole> senderRoles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            senderRoles.add(USER);
            senderRoles.add(ASSISTANT);
        }
        return senderRoles;
    }
}
