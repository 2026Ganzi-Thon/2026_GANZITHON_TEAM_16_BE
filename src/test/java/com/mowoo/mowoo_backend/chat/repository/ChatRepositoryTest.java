package com.mowoo.mowoo_backend.chat.repository;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.chat.entity.ChatMessageAttachment;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import com.mowoo.mowoo_backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 로컬 MySQL에서 실행하고 테스트마다 롤백한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ChatSessionRepository sessionRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private ChatMessageAttachmentRepository attachmentRepository;

    private User user;
    private AiCharacter character;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        user = em.persist(new User("은송", null, null));
        character = em.persist(new AiCharacter(uniqueCode(), "모우", null, null, false));
        session = em.persist(new ChatSession(user, character));
    }

    @Test
    void 세션은_본인_ID로만_조회된다() {
        User otherUser = em.persist(new User("다른 사용자", null, null));

        assertThat(sessionRepository.findByIdAndUserId(session.getId(), user.getId())).contains(session);
        assertThat(sessionRepository.findByIdAndUserId(session.getId(), otherUser.getId())).isEmpty();
    }

    @Test
    void 세션_시작_시각이_저장되고_종료_전에는_endedAt이_비어_있다() {
        em.flush();
        em.clear();

        ChatSession found = sessionRepository.findById(session.getId()).orElseThrow();

        assertThat(found.getStartedAt()).isNotNull();
        assertThat(found.getEndedAt()).isNull();
        assertThat(found.isEnded()).isFalse();
    }

    @Test
    void 가장_큰_순번의_메시지를_찾는다() {
        em.persist(ChatMessage.ofUser(session, 1, "오늘 면접이 걱정돼요."));
        ChatMessage latest = em.persist(ChatMessage.ofAssistant(session, 2, character, "어떤 점이 가장 걱정되나요?"));

        assertThat(messageRepository.findTopBySessionIdOrderBySequenceNoDesc(session.getId())).contains(latest);
    }

    @Test
    void 발신자_순서를_순번대로_읽는다() {
        em.persist(ChatMessage.ofAssistant(session, 2, character, "어떤 점이 가장 걱정되나요?"));
        em.persist(ChatMessage.ofUser(session, 1, "오늘 면접이 걱정돼요."));
        em.persist(ChatMessage.ofUser(session, 3, "말을 잘 못할까 봐요."));

        assertThat(messageRepository.findSenderRolesBySessionId(session.getId()))
                .containsExactly(SenderRole.USER, SenderRole.ASSISTANT, SenderRole.USER);
    }

    @Test
    void 사용자_메시지는_캐릭터가_없고_AI_응답은_캐릭터가_있다() {
        ChatMessage userMessage = em.persist(ChatMessage.ofUser(session, 1, "오늘 면접이 걱정돼요."));
        ChatMessage assistantMessage = em.persist(ChatMessage.ofAssistant(session, 2, character, "어떤 점이 가장 걱정되나요?"));

        assertThat(userMessage.getCharacter()).isNull();
        assertThat(assistantMessage.getCharacter()).isEqualTo(character);
    }

    @Test
    void 같은_세션에서_순번이_겹치면_저장되지_않는다() {
        messageRepository.saveAndFlush(ChatMessage.ofUser(session, 1, "첫 메시지"));

        assertThatThrownBy(() -> messageRepository.saveAndFlush(ChatMessage.ofUser(session, 1, "같은 순번")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 첨부를_메시지별로_정렬_순서대로_읽는다() {
        ChatMessage message = em.persist(ChatMessage.ofUser(session, 1, null));
        em.persist(new ChatMessageAttachment(message, "chat/b.jpg", "image/jpeg", 2));
        em.persist(new ChatMessageAttachment(message, "chat/a.jpg", "image/jpeg", 1));

        List<ChatMessageAttachment> attachments = attachmentRepository.findByMessageIdInOrderBySortOrder(
                List.of(message.getId()));

        assertThat(attachments).extracting(ChatMessageAttachment::getStorageKey)
                .containsExactly("chat/a.jpg", "chat/b.jpg");
    }

    @Test
    void 세션을_지우면_DB에서_메시지와_첨부도_함께_지워진다() {
        ChatMessage message = em.persist(ChatMessage.ofUser(session, 1, "사진을 보냈어요."));
        ChatMessageAttachment attachment = em.persist(new ChatMessageAttachment(message, "chat/a.jpg", "image/jpeg", 1));
        em.flush();
        em.clear();

        // ON DELETE CASCADE가 DB 외래키에 걸렸는지 확인하려고 JPA를 거치지 않고 지운다.
        em.getEntityManager()
                .createNativeQuery("delete from chat_sessions where id = :id")
                .setParameter("id", session.getId().toString())
                .executeUpdate();

        assertThat(messageRepository.findById(message.getId())).isEmpty();
        assertThat(attachmentRepository.findById(attachment.getId())).isEmpty();
    }

    private String uniqueCode() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
