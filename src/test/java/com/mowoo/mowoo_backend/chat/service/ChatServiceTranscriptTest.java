package com.mowoo.mowoo_backend.chat.service;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.chat.ai.ChatAiClient;
import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import com.mowoo.mowoo_backend.chat.exception.ChatErrorCode;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 로컬 MySQL에서 실행하고 테스트마다 롤백한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
class ChatServiceTranscriptTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ChatService chatService;

    @MockitoBean
    private ChatAiClient chatAiClient;

    private User user;
    private AiCharacter character;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        user = em.persist(new User("은송", null, null));
        character = em.persist(new AiCharacter(uniqueCode(), "다정한 모우", null, null, false));
        session = em.persist(new ChatSession(user, character));
    }

    @Test
    void 대화_기록을_순번_순서로_돌려주고_AI_응답에는_캐릭터_이름을_담는다() {
        em.persist(ChatMessage.ofAssistant(session, 2, character, "어떤 점이 가장 걱정되나요?"));
        em.persist(ChatMessage.ofUser(session, 1, "오늘 면접이 걱정돼요."));
        em.flush();
        em.clear();

        ChatTranscript transcript = chatService.getTranscript(user.getId(), session.getId());

        assertThat(transcript.sessionId()).isEqualTo(session.getId());
        assertThat(transcript.startedAt()).isNotNull();
        assertThat(transcript.endedAt()).isNull();
        assertThat(transcript.messages()).extracting(ChatTranscript.Message::sequenceNo).containsExactly(1, 2);
        assertThat(transcript.messages().get(0).senderRole()).isEqualTo(SenderRole.USER);
        assertThat(transcript.messages().get(0).characterName()).isNull();
        assertThat(transcript.messages().get(0).content()).isEqualTo("오늘 면접이 걱정돼요.");
        assertThat(transcript.messages().get(1).characterName()).isEqualTo("다정한 모우");
        assertThat(transcript.messages().get(1).createdAt()).isNotNull();
    }

    @Test
    void 왕복이_5회_미만이면_일기를_만들_수_없다고_알려준다() {
        addTurns(4);

        ChatTranscript transcript = chatService.getTranscript(user.getId(), session.getId());

        assertThat(transcript.completedTurns()).isEqualTo(4);
        assertThat(transcript.canGenerateDiary()).isFalse();
    }

    @Test
    void 왕복이_5회_이상이면_일기를_만들_수_있다고_알려준다() {
        addTurns(5);

        ChatTranscript transcript = chatService.getTranscript(user.getId(), session.getId());

        assertThat(transcript.completedTurns()).isEqualTo(5);
        assertThat(transcript.canGenerateDiary()).isTrue();
        assertThat(transcript.messages()).hasSize(10);
    }

    @Test
    void 종료된_대화도_읽을_수_있고_종료_시각을_담는다() {
        chatService.endSession(user.getId(), session.getId());

        ChatTranscript transcript = chatService.getTranscript(user.getId(), session.getId());

        assertThat(transcript.endedAt()).isNotNull();
    }

    @Test
    void 다른_사용자의_대화는_SESSION_NOT_FOUND다() {
        User otherUser = em.persist(new User("다른 사용자", null, null));

        assertThatThrownBy(() -> chatService.getTranscript(otherUser.getId(), session.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ChatErrorCode.SESSION_NOT_FOUND);
    }

    // 사용자 메시지와 AI 응답을 번갈아 저장한다. 순번은 1부터 시작한다.
    private void addTurns(int turns) {
        for (int turn = 0; turn < turns; turn++) {
            em.persist(ChatMessage.ofUser(session, turn * 2 + 1, "사용자 메시지 " + turn));
            em.persist(ChatMessage.ofAssistant(session, turn * 2 + 2, character, "AI 응답 " + turn));
        }
        em.flush();
        em.clear();
    }

    private String uniqueCode() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
