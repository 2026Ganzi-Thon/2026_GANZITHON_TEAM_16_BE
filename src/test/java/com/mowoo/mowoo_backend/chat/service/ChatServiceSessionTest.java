package com.mowoo.mowoo_backend.chat.service;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import com.mowoo.mowoo_backend.chat.ai.StubChatAiClient;
import com.mowoo.mowoo_backend.chat.dto.request.StartChatSessionRequest;
import com.mowoo.mowoo_backend.chat.dto.response.ChatMessageResponse;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionDetailResponse;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionItem;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionResponse;
import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.chat.exception.ChatErrorCode;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;
import com.mowoo.mowoo_backend.global.request.CursorRequest;
import com.mowoo.mowoo_backend.global.response.CursorResponse;
import com.mowoo.mowoo_backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 로컬 MySQL에서 실행하고 테스트마다 롤백한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ChatService.class, StubChatAiClient.class})
class ChatServiceSessionTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ChatService chatService;

    private User user;
    private AiCharacter character;

    @BeforeEach
    void setUp() {
        user = em.persist(new User("은송", null, null));
        character = em.persist(new AiCharacter(uniqueCode(), "다정한 모우", null, null, false));
        em.persist(new UserCharacter(user, character));
    }

    @Test
    void 보유한_캐릭터로_대화를_시작한다() {
        ChatSessionResponse response = chatService.startSession(user.getId(), new StartChatSessionRequest(character.getId()));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.code()).isEqualTo("CHAT_SESSION_START_SUCCESS");
        assertThat(response.id()).isNotNull();
        assertThat(response.characterId()).isEqualTo(character.getId());
        assertThat(response.title()).isNull();
        assertThat(response.summary()).isNull();
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.endedAt()).isNull();
        assertThat(response.completedTurns()).isZero();
        assertThat(response.canGenerateDiary()).isFalse();
    }

    @Test
    void 대화_시작_응답의_startedAt은_이후_조회와_같다() {
        ChatSessionResponse started = chatService.startSession(user.getId(), new StartChatSessionRequest(character.getId()));
        em.clear();

        ChatSessionItem found = chatService.getSession(user.getId(), started.id(), new CursorRequest(null, null))
                .session();

        assertThat(found.startedAt()).isEqualTo(started.startedAt());
    }

    @Test
    void 보유하지_않은_캐릭터로는_대화를_시작할_수_없다() {
        AiCharacter premium = em.persist(new AiCharacter(uniqueCode(), "퀸 마인드 모우", null, null, true));

        assertErrorCode(
                () -> chatService.startSession(user.getId(), new StartChatSessionRequest(premium.getId())),
                ChatErrorCode.CHARACTER_NOT_OWNED);
    }

    @Test
    void 없는_캐릭터로는_대화를_시작할_수_없다() {
        assertErrorCode(
                () -> chatService.startSession(user.getId(), new StartChatSessionRequest(UUID.randomUUID())),
                ChatErrorCode.CHARACTER_NOT_OWNED);
    }

    @Test
    void 보관함은_최신순으로_커서를_따라_끝까지_읽는다() {
        Instant base = Instant.parse("2026-11-01T00:00:00Z");
        ChatSession oldest = sessionStartedAt(base);
        ChatSession middle = sessionStartedAt(base.plus(1, ChronoUnit.DAYS));
        ChatSession latest = sessionStartedAt(base.plus(2, ChronoUnit.DAYS));

        CursorResponse<ChatSessionItem> first = chatService.getSessions(user.getId(), new CursorRequest(2, null));
        CursorResponse<ChatSessionItem> second = chatService.getSessions(
                user.getId(), new CursorRequest(2, first.nextCursor()));

        assertThat(first.items()).extracting(ChatSessionItem::id).containsExactly(latest.getId(), middle.getId());
        assertThat(first.nextCursor()).isNotNull();
        assertThat(second.items()).extracting(ChatSessionItem::id).containsExactly(oldest.getId());
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void 보관함에서_시작_시각이_같은_세션도_빠짐없이_읽는다() {
        Instant sameTime = Instant.parse("2026-11-01T00:00:00Z");
        List<UUID> ids = List.of(
                sessionStartedAt(sameTime).getId(),
                sessionStartedAt(sameTime).getId(),
                sessionStartedAt(sameTime).getId());

        CursorResponse<ChatSessionItem> first = chatService.getSessions(user.getId(), new CursorRequest(2, null));
        CursorResponse<ChatSessionItem> second = chatService.getSessions(
                user.getId(), new CursorRequest(2, first.nextCursor()));

        List<UUID> read = new ArrayList<>(first.items().stream().map(ChatSessionItem::id).toList());
        read.addAll(second.items().stream().map(ChatSessionItem::id).toList());
        assertThat(read).containsExactlyInAnyOrderElementsOf(ids).doesNotHaveDuplicates();
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void 보관함에는_다른_사용자의_대화가_없고_세션별_왕복_수가_계산된다() {
        User otherUser = em.persist(new User("다른 사용자", null, null));
        em.persist(new ChatSession(otherUser, character));
        ChatSession session = em.persist(new ChatSession(user, character));
        addTurns(session, 5);

        CursorResponse<ChatSessionItem> response = chatService.getSessions(
                user.getId(), new CursorRequest(null, null));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(session.getId());
            assertThat(item.completedTurns()).isEqualTo(5);
            assertThat(item.canGenerateDiary()).isTrue();
        });
    }

    @Test
    void 대화_상세는_최근_메시지부터_페이지를_나누고_페이지_안은_순번_순서다() {
        ChatSession session = em.persist(new ChatSession(user, character));
        addTurns(session, 3); // 순번 1~6

        ChatSessionDetailResponse first = chatService.getSession(user.getId(), session.getId(), new CursorRequest(4, null));
        ChatSessionDetailResponse second = chatService.getSession(
                user.getId(), session.getId(), new CursorRequest(4, first.messages().nextCursor()));

        assertThat(first.messages().items()).extracting(ChatMessageResponse::sequenceNo).containsExactly(3, 4, 5, 6);
        assertThat(first.messages().nextCursor()).isNotNull();
        assertThat(second.messages().items()).extracting(ChatMessageResponse::sequenceNo).containsExactly(1, 2);
        assertThat(second.messages().nextCursor()).isNull();
        assertThat(first.session().completedTurns()).isEqualTo(3);
    }

    @Test
    void 대화_상세의_메시지는_발신자와_응답_캐릭터를_담는다() {
        ChatSession session = em.persist(new ChatSession(user, character));
        addTurns(session, 1);

        List<ChatMessageResponse> items = chatService.getSession(
                user.getId(), session.getId(), new CursorRequest(null, null)).messages().items();

        assertThat(items.get(0).characterId()).isNull();
        assertThat(items.get(1).characterId()).isEqualTo(character.getId());
    }

    @Test
    void 다른_사용자의_대화는_조회_종료_삭제할_수_없다() {
        User otherUser = em.persist(new User("다른 사용자", null, null));
        ChatSession othersSession = em.persist(new ChatSession(otherUser, character));
        UUID sessionId = othersSession.getId();

        assertErrorCode(() -> chatService.getSession(user.getId(), sessionId, new CursorRequest(null, null)),
                ChatErrorCode.SESSION_NOT_FOUND);
        assertErrorCode(() -> chatService.endSession(user.getId(), sessionId), ChatErrorCode.SESSION_NOT_FOUND);
        assertErrorCode(() -> chatService.deleteSession(user.getId(), sessionId), ChatErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void 읽을_수_없는_커서는_VALIDATION_ERROR다() {
        ChatSession session = em.persist(new ChatSession(user, character));

        assertErrorCode(() -> chatService.getSessions(user.getId(), new CursorRequest(null, "not-a-cursor")),
                CommonErrorCode.VALIDATION_ERROR);
        assertErrorCode(() -> chatService.getSession(user.getId(), session.getId(), new CursorRequest(null, "!!")),
                CommonErrorCode.VALIDATION_ERROR);
    }

    @Test
    void 대화를_종료하면_종료_시각이_기록되고_다시_종료해도_같은_결과다() {
        ChatSession session = em.persist(new ChatSession(user, character));
        addTurns(session, 2);

        ChatSessionResponse ended = chatService.endSession(user.getId(), session.getId());
        em.flush();
        em.clear();
        ChatSessionResponse endedAgain = chatService.endSession(user.getId(), session.getId());

        assertThat(ended.code()).isEqualTo("CHAT_SESSION_END_SUCCESS");
        assertThat(ended.endedAt()).isNotNull();
        assertThat(ended.completedTurns()).isEqualTo(2);
        assertThat(endedAgain).isEqualTo(ended);
    }

    @Test
    void 대화를_삭제하면_메시지도_함께_지워진다() {
        ChatSession session = em.persist(new ChatSession(user, character));
        ChatMessage message = em.persist(ChatMessage.ofUser(session, 1, "오늘 면접이 걱정돼요."));
        em.flush();
        em.clear();

        chatService.deleteSession(user.getId(), session.getId());
        em.flush();
        em.clear();

        assertThat(em.find(ChatSession.class, session.getId())).isNull();
        assertThat(em.find(ChatMessage.class, message.getId())).isNull();
    }

    private ChatSession sessionStartedAt(Instant startedAt) {
        ChatSession session = em.persistAndFlush(new ChatSession(user, character));
        em.getEntityManager()
                .createNativeQuery("update chat_sessions set started_at = :startedAt where id = :id")
                .setParameter("startedAt", startedAt)
                .setParameter("id", session.getId().toString())
                .executeUpdate();
        em.clear();
        return session;
    }

    // 사용자 메시지와 AI 응답을 번갈아 저장한다. 순번은 1부터 시작한다.
    private void addTurns(ChatSession session, int turns) {
        for (int turn = 0; turn < turns; turn++) {
            em.persist(ChatMessage.ofUser(session, turn * 2 + 1, "사용자 메시지 " + turn));
            em.persist(ChatMessage.ofAssistant(session, turn * 2 + 2, character, "AI 응답 " + turn));
        }
        em.flush();
        em.clear();
    }

    private void assertErrorCode(Runnable call, Object errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    private String uniqueCode() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
