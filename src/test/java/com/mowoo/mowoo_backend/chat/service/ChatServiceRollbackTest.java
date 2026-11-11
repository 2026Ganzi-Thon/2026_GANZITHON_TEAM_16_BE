package com.mowoo.mowoo_backend.chat.service;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import com.mowoo.mowoo_backend.chat.ai.ChatAiClient;
import com.mowoo.mowoo_backend.chat.ai.ChatSummaryAiClient;
import com.mowoo.mowoo_backend.chat.ai.DiaryComparisonAiClient;
import com.mowoo.mowoo_backend.chat.ai.DiarySuggestionAiClient;
import com.mowoo.mowoo_backend.chat.ai.ChatAiException;
import com.mowoo.mowoo_backend.chat.dto.request.SendMessageRequest;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.chat.exception.ChatErrorCode;
import com.mowoo.mowoo_backend.chat.repository.ChatMessageRepository;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

// 서비스 트랜잭션이 실제로 롤백되는지 보려고 테스트 트랜잭션 없이 실행한다.
// 로컬 MySQL에 데이터를 직접 넣으므로 테스트가 끝나면 지운다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ChatService.class)
class ChatServiceRollbackTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ChatAiClient chatAiClient;

    @MockitoBean
    private ChatSummaryAiClient chatSummaryAiClient;

    @MockitoBean
    private DiarySuggestionAiClient diarySuggestionAiClient;

    @MockitoBean
    private DiaryComparisonAiClient diaryComparisonAiClient;

    private TransactionTemplate tx;
    private UUID userId;
    private UUID characterId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            User user = new User("은송", null, null);
            AiCharacter character = new AiCharacter("test-" + UUID.randomUUID().toString().substring(0, 8),
                    "다정한 모우", null, null, false);
            em.persist(user);
            em.persist(character);
            em.persist(new UserCharacter(user, character));
            ChatSession session = new ChatSession(user, character);
            em.persist(session);
            userId = user.getId();
            characterId = character.getId();
            sessionId = session.getId();
        });
    }

    @AfterEach
    void tearDown() {
        // 메시지는 세션 삭제 시 DB에서 함께 지워지고, 보유 기록은 사용자 삭제 시 함께 지워진다.
        tx.executeWithoutResult(status -> {
            deleteById("chat_sessions", sessionId);
            deleteById("users", userId);
            deleteById("ai_characters", characterId);
        });
    }

    @Test
    void AI가_실패하면_사용자_메시지도_저장되지_않는다() {
        given(chatAiClient.reply(any())).willThrow(new ChatAiException("시간 초과", null));

        assertThatThrownBy(() -> chatService.sendMessage(
                userId, sessionId, new SendMessageRequest("오늘 면접이 걱정돼요.", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ChatErrorCode.AI_UNAVAILABLE);

        assertThat(messageRepository.findBySessionIdOrderBySequenceNo(sessionId)).isEmpty();
    }

    private void deleteById(String table, UUID id) {
        em.createNativeQuery("delete from " + table + " where id = :id")
                .setParameter("id", id.toString())
                .executeUpdate();
    }
}
