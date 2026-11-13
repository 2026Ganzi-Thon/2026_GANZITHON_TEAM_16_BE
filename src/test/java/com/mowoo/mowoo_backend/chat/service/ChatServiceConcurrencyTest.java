package com.mowoo.mowoo_backend.chat.service;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import com.mowoo.mowoo_backend.chat.ai.ChatAiClient;
import com.mowoo.mowoo_backend.chat.ai.ChatSummaryAiClient;
import com.mowoo.mowoo_backend.chat.ai.DiaryComparisonAiClient;
import com.mowoo.mowoo_backend.chat.ai.DiarySuggestionAiClient;
import com.mowoo.mowoo_backend.chat.dto.request.CreateDiaryReferenceRequest;
import com.mowoo.mowoo_backend.chat.dto.request.SendMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.response.ChatMessageResponse;
import com.mowoo.mowoo_backend.chat.dto.response.CreateDiaryReferenceResponse;
import com.mowoo.mowoo_backend.chat.dto.response.SendMessageResponse;
import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.chat.exception.ChatErrorCode;
import com.mowoo.mowoo_backend.chat.repository.ChatSessionRepository;
import com.mowoo.mowoo_backend.diary.entity.Diary;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.record.service.RecordService;
import com.mowoo.mowoo_backend.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

// 같은 세션이나 일기에 겹친 요청을 각자의 트랜잭션으로 실행하려고 테스트 트랜잭션 없이 실행한다.
// 로컬 MySQL에 데이터를 직접 넣으므로 테스트가 끝나면 지운다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ChatService.class, RecordService.class})
class ChatServiceConcurrencyTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private RecordService recordService;

    @Autowired
    private ChatSessionRepository sessionRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

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

    private UUID userId;
    private UUID characterId;
    private UUID sessionId;
    private UUID concernId;
    private UUID diaryId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = new User("은송", null, null);
            user.updateProfile("은송", null, null, true, false);
            AiCharacter character = new AiCharacter("test-" + UUID.randomUUID().toString().substring(0, 8),
                    "다정한 모우", null, null, false);
            em.persist(user);
            em.persist(character);
            em.persist(new UserCharacter(user, character));
            ChatSession session = new ChatSession(user, character);
            em.persist(session);
            ChatMessage concern = ChatMessage.ofUser(session, 1, "내일 발표가 너무 걱정돼요.");
            em.persist(concern);
            em.persist(ChatMessage.ofAssistant(session, 2, character, "어떤 점이 가장 걱정되나요?"));
            Diary diary = new Diary(user, LocalDate.of(2026, 9, 15), "발표를 앞둔 날", "발표 전날 잠이 오지 않았다.",
                    Instant.now());
            em.persist(diary);
            userId = user.getId();
            characterId = character.getId();
            sessionId = session.getId();
            concernId = concern.getId();
            diaryId = diary.getId();
        });
    }

    @AfterEach
    void tearDown() {
        // 메시지와 일기 참조는 세션·일기 삭제 시 DB에서 함께 지워지고, 보유 기록은 사용자 삭제 시 함께 지워진다.
        jdbc.update("delete from diaries where user_id = ?", userId.toString());
        jdbc.update("delete from chat_sessions where id = ?", sessionId.toString());
        jdbc.update("delete from users where id = ?", userId.toString());
        jdbc.update("delete from ai_characters where id = ?", characterId.toString());
    }

    @Test
    void 메시지_전송이_끝나기를_기다린_과거_일기_연결은_그다음_순번으로_비교_답변을_저장한다() throws Exception {
        CountDownLatch sendHoldsLock = new CountDownLatch(1);
        CountDownLatch referenceWaiting = new CountDownLatch(1);
        // 메시지 전송이 세션을 잠근 채 AI 응답을 기다리는 동안 연결 요청을 보내 잠금을 기다리게 한다.
        given(chatAiClient.reply(any())).willAnswer(invocation -> {
            sendHoldsLock.countDown();
            assertThat(referenceWaiting.await(10, TimeUnit.SECONDS)).isTrue();
            return "청중 앞에 서는 게 걱정되나요?";
        });
        given(diaryComparisonAiClient.compare(any())).willReturn("두 기록 모두 다른 사람의 평가를 걱정했어요.");

        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<SendMessageResponse> send = workers.submit(() -> chatService.sendMessage(
                    userId, sessionId, new SendMessageRequest("사람이 많을까 봐요.", null)));
            assertThat(sendHoldsLock.await(10, TimeUnit.SECONDS)).isTrue();
            Future<CreateDiaryReferenceResponse> reference = workers.submit(() -> chatService.createDiaryReference(
                    userId, sessionId, new CreateDiaryReferenceRequest(concernId, diaryId)));
            awaitLockWait(reference, "chat_sessions");
            referenceWaiting.countDown();

            assertThat(send.get(20, TimeUnit.SECONDS).assistantMessage().sequenceNo()).isEqualTo(4);
            assertThat(reference.get(20, TimeUnit.SECONDS).assistantMessage().sequenceNo()).isEqualTo(5);
        }
        assertThat(jdbc.queryForList("select sequence_no from chat_messages where session_id = ? order by sequence_no",
                Integer.class, sessionId.toString())).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void 비교_답변을_기다리는_동안_들어온_일기_삭제는_연결이_끝난_뒤_일기와_참조를_지운다() throws Exception {
        CountDownLatch referenceHoldsLock = new CountDownLatch(1);
        CountDownLatch deleteWaiting = new CountDownLatch(1);
        // 연결 요청이 AI 비교 답변을 기다리는 동안 같은 일기를 선택 삭제한다.
        given(diaryComparisonAiClient.compare(any())).willAnswer(invocation -> {
            referenceHoldsLock.countDown();
            assertThat(deleteWaiting.await(10, TimeUnit.SECONDS)).isTrue();
            return "두 기록 모두 다른 사람의 평가를 걱정했어요.";
        });

        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<CreateDiaryReferenceResponse> reference = workers.submit(() -> chatService.createDiaryReference(
                    userId, sessionId, new CreateDiaryReferenceRequest(concernId, diaryId)));
            assertThat(referenceHoldsLock.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> delete = workers.submit(() -> recordService.deleteDiaries(userId, List.of(diaryId)));
            awaitLockWait(delete, "diaries");
            deleteWaiting.countDown();

            assertThat(reference.get(20, TimeUnit.SECONDS).assistantMessage().diaryReferences())
                    .extracting(ChatMessageResponse.DiaryReference::diaryId).containsExactly(diaryId);
            delete.get(20, TimeUnit.SECONDS);
        }
        // 삭제는 연결이 끝난 뒤 일기와 참조를 지우고, 비교 답변 메시지는 남긴다.
        assertThat(jdbc.queryForObject("select count(*) from diaries where id = ?",
                Integer.class, diaryId.toString())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from diary_references where diary_id = ?",
                Integer.class, diaryId.toString())).isZero();
        assertThat(jdbc.queryForList("select sequence_no from chat_messages where session_id = ? order by sequence_no",
                Integer.class, sessionId.toString())).containsExactly(1, 2, 3);
    }

    @Test
    void 다른_요청이_지우는_중인_대화를_삭제하면_SESSION_NOT_FOUND다() throws Exception {
        CountDownLatch deleteAllHoldsLock = new CountDownLatch(1);
        CountDownLatch deleteWaiting = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            // 전체 삭제처럼 세션을 잠그고 지운 뒤, 대화 삭제가 잠금을 기다릴 때까지 커밋하지 않는다.
            Future<?> deleteAll = workers.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        sessionRepository.findAllForUserForUpdate(userId);
                        chatService.deleteAllSessions(userId);
                        deleteAllHoldsLock.countDown();
                        await(deleteWaiting);
                    }));
            assertThat(deleteAllHoldsLock.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> delete = workers.submit(() -> chatService.deleteSession(userId, sessionId));
            awaitLockWait(delete, "chat_sessions");
            deleteWaiting.countDown();

            deleteAll.get(20, TimeUnit.SECONDS);
            assertThatThrownBy(() -> delete.get(20, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.SESSION_NOT_FOUND);
        }
        assertThat(jdbc.queryForObject("select count(*) from chat_sessions where id = ?",
                Integer.class, sessionId.toString())).isZero();
    }

    // 다른 요청이 table의 행 잠금(select ... for update, delete)을 기다리기 시작할 때까지 기다린다.
    // 잠금을 쥔 동안에는 이 문장들이 끝나지 않으므로 processlist에 보이면 기다리는 중이다. 잠금 없이 먼저 끝나면 바로 돌아간다.
    // 잠금 없이 읽은 행을 지우면 커밋 시점의 delete에서 기다리므로 그 문장도 본다.
    private void awaitLockWait(Future<?> request, String table) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!request.isDone() && jdbc.queryForObject("select count(*) from information_schema.processlist "
                        + "where id <> connection_id() and (info like ? or info like ?)",
                Integer.class, "%from " + table + "%for update%", "delete from " + table + "%") == 0) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("잠금을 기다리는 요청이 없습니다.");
            }
            Thread.sleep(20);
        }
    }

    // 트랜잭션 콜백 안에서는 검사 예외를 던질 수 없어서 InterruptedException을 감싼다.
    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
