package com.mowoo.mowoo_backend.chat.service;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import com.mowoo.mowoo_backend.chat.ai.ChatAiClient;
import com.mowoo.mowoo_backend.chat.ai.ChatAiException;
import com.mowoo.mowoo_backend.chat.ai.ChatSummaryAiClient;
import com.mowoo.mowoo_backend.chat.ai.DiaryComparisonAiClient;
import com.mowoo.mowoo_backend.chat.ai.DiarySuggestionAiClient;
import com.mowoo.mowoo_backend.chat.dto.request.CreateDiaryReferenceRequest;
import com.mowoo.mowoo_backend.chat.dto.response.ChatMessageResponse;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionDetailResponse;
import com.mowoo.mowoo_backend.chat.dto.response.CreateDiaryReferenceResponse;
import com.mowoo.mowoo_backend.chat.dto.response.DiarySuggestionResponse;
import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.chat.entity.DiaryReference;
import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import com.mowoo.mowoo_backend.chat.exception.ChatErrorCode;
import com.mowoo.mowoo_backend.diary.entity.Diary;
import com.mowoo.mowoo_backend.diary.exception.DiaryErrorCode;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.request.CursorRequest;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import com.mowoo.mowoo_backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 로컬 MySQL에서 실행하고 테스트마다 롤백한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
class ChatServiceDiaryReferenceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ChatService chatService;

    @MockitoBean
    private ChatAiClient chatAiClient;

    @MockitoBean
    private ChatSummaryAiClient chatSummaryAiClient;

    @MockitoBean
    private DiarySuggestionAiClient diarySuggestionAiClient;

    @MockitoBean
    private DiaryComparisonAiClient diaryComparisonAiClient;

    private User user;
    private AiCharacter character;
    private ChatSession session;
    private ChatMessage concern;
    private ChatMessage reply;

    @BeforeEach
    void setUp() {
        user = em.persist(new User("은송", null, null));
        user.updateProfile("은송", null, null, true, false);
        character = em.persist(new AiCharacter(uniqueCode(), "다정한 모우", "따뜻하게 들어 주는 모우", null, false));
        em.persist(new UserCharacter(user, character));
        session = em.persist(new ChatSession(user, character));
        concern = em.persist(ChatMessage.ofUser(session, 1, "내일 발표가 너무 걱정돼요."));
        reply = em.persist(ChatMessage.ofAssistant(session, 2, character, "어떤 점이 가장 걱정되나요?"));
        em.flush();
    }

    @Test
    void 후보는_최근_저장_일기_중_AI가_고른_순서대로_돌려준다() {
        Diary presentation = savedDiary(user, LocalDate.of(2026, 9, 15), "발표를 앞둔 날", "발표 전날 잠이 오지 않았다.");
        Diary trip = savedDiary(user, LocalDate.of(2026, 10, 1), "여행", "바다를 보고 왔다.");
        em.persist(new Diary(user, LocalDate.of(2026, 10, 20), "저장 안 한 초안", "초안", null));
        savedDiary(em.persist(new User("다른 사용자", null, null)), LocalDate.of(2026, 10, 5), "남의 일기", "본문");
        Diary fromThisSession = Diary.draft(user, session, LocalDate.of(2026, 11, 7), "이 대화로 만든 일기", "본문");
        fromThisSession.update(null, null, null, true, Instant.now());
        em.persist(fromThisSession);
        em.flush();
        given(diarySuggestionAiClient.select(any(), any(), anyInt())).willReturn(List.of(1));

        ListResponse<DiarySuggestionResponse> response =
                chatService.getDiarySuggestions(user.getId(), session.getId(), concern.getId());

        assertThat(response.code()).isEqualTo("DIARY_SUGGESTION_READ_SUCCESS");
        assertThat(response.items()).containsExactly(
                new DiarySuggestionResponse(presentation.getId(), presentation.getEntryDate(), presentation.getTitle()));
        ArgumentCaptor<List<DiarySuggestionAiClient.Candidate>> candidates = ArgumentCaptor.captor();
        verify(diarySuggestionAiClient).select(eq("내일 발표가 너무 걱정돼요."), candidates.capture(), eq(3));
        // 최신순으로 넘기고, 초안·다른 사용자 일기·이 대화로 만든 일기는 뺀다.
        assertThat(candidates.getValue()).extracting(DiarySuggestionAiClient.Candidate::title)
                .containsExactly(trip.getTitle(), presentation.getTitle());
    }

    @Test
    void 저장_일기가_없으면_AI를_부르지_않고_빈_목록을_돌려준다() {
        ListResponse<DiarySuggestionResponse> response =
                chatService.getDiarySuggestions(user.getId(), session.getId(), concern.getId());

        assertThat(response.items()).isEmpty();
        verifyNoInteractions(diarySuggestionAiClient);
    }

    @Test
    void 후보_선택에_실패하면_빈_목록을_돌려준다() {
        savedDiary(user, LocalDate.of(2026, 9, 15), "발표를 앞둔 날", "본문");
        given(diarySuggestionAiClient.select(any(), any(), anyInt())).willThrow(new ChatAiException("실패", null));

        assertThat(chatService.getDiarySuggestions(user.getId(), session.getId(), concern.getId()).items()).isEmpty();
    }

    @Test
    void 과거_일기_활용을_끄면_PAST_DIARY_DISABLED다() {
        Diary diary = savedDiary(user, LocalDate.of(2026, 9, 15), "발표를 앞둔 날", "본문");
        user.updateProfile("은송", null, null, false, false);

        assertErrorCode(() -> chatService.getDiarySuggestions(user.getId(), session.getId(), concern.getId()),
                ChatErrorCode.PAST_DIARY_DISABLED);
        assertErrorCode(() -> chatService.createDiaryReference(user.getId(), session.getId(),
                new CreateDiaryReferenceRequest(concern.getId(), diary.getId())), ChatErrorCode.PAST_DIARY_DISABLED);
        verifyNoInteractions(diarySuggestionAiClient, diaryComparisonAiClient);
    }

    @Test
    void 본인_대화의_사용자_메시지가_아니면_MESSAGE_NOT_FOUND다() {
        Diary diary = savedDiary(user, LocalDate.of(2026, 9, 15), "발표를 앞둔 날", "본문");
        User other = em.persist(new User("다른 사용자", null, null));
        ChatSession othersSession = em.persist(new ChatSession(other, character));

        assertErrorCode(() -> chatService.getDiarySuggestions(user.getId(), session.getId(), reply.getId()),
                ChatErrorCode.MESSAGE_NOT_FOUND);
        assertErrorCode(() -> chatService.getDiarySuggestions(user.getId(), session.getId(), UUID.randomUUID()),
                ChatErrorCode.MESSAGE_NOT_FOUND);
        assertErrorCode(() -> chatService.getDiarySuggestions(user.getId(), othersSession.getId(), concern.getId()),
                ChatErrorCode.MESSAGE_NOT_FOUND);
        assertErrorCode(() -> chatService.createDiaryReference(user.getId(), session.getId(),
                new CreateDiaryReferenceRequest(reply.getId(), diary.getId())), ChatErrorCode.MESSAGE_NOT_FOUND);
    }

    @Test
    void 연결에_동의하면_비교_답변을_대화_끝에_저장하고_일기_참조를_단다() {
        Diary diary = savedDiary(user, LocalDate.of(2026, 9, 15), "발표를 앞둔 날", "발표 전날 잠이 오지 않았다.");
        given(diaryComparisonAiClient.compare(any())).willReturn("두 기록 모두 다른 사람의 평가를 걱정했어요.");

        CreateDiaryReferenceResponse response = chatService.createDiaryReference(user.getId(), session.getId(),
                new CreateDiaryReferenceRequest(concern.getId(), diary.getId()));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.code()).isEqualTo("DIARY_REFERENCE_CREATE_SUCCESS");
        ChatMessageResponse message = response.assistantMessage();
        assertThat(message.sequenceNo()).isEqualTo(3);
        assertThat(message.senderRole()).isEqualTo(SenderRole.ASSISTANT);
        assertThat(message.characterId()).isEqualTo(character.getId());
        assertThat(message.content()).isEqualTo("두 기록 모두 다른 사람의 평가를 걱정했어요.");
        assertThat(message.createdAt()).isNotNull();
        assertThat(message.diaryReferences()).singleElement().satisfies(reference -> {
            assertThat(reference.diaryId()).isEqualTo(diary.getId());
            assertThat(reference.entryDate()).isEqualTo(LocalDate.of(2026, 9, 15));
            assertThat(reference.title()).isEqualTo("발표를 앞둔 날");
            assertThat(reference.acceptedAt()).isNotNull();
        });
        verify(diaryComparisonAiClient).compare(new DiaryComparisonAiClient.Request(
                "다정한 모우", "따뜻하게 들어 주는 모우", "내일 발표가 너무 걱정돼요.",
                LocalDate.of(2026, 9, 15), "발표를 앞둔 날", "발표 전날 잠이 오지 않았다."));

        em.flush();
        em.clear();
        ChatSessionDetailResponse detail =
                chatService.getSession(user.getId(), session.getId(), new CursorRequest(null, null));
        assertThat(detail.messages().items()).extracting(ChatMessageResponse::diaryReferences)
                .containsExactly(List.of(), List.of(), message.diaryReferences());
        // 사용자 메시지 없이 추가된 답변은 왕복 수에 들어가지 않는다.
        assertThat(detail.session().completedTurns()).isEqualTo(1);
    }

    @Test
    void 저장하지_않았거나_다른_사용자의_일기는_DIARY_NOT_FOUND다() {
        Diary draft = em.persist(new Diary(user, LocalDate.of(2026, 10, 20), "초안", "초안", null));
        Diary others = savedDiary(em.persist(new User("다른 사용자", null, null)), LocalDate.of(2026, 10, 5), "남의 일기", "본문");

        assertErrorCode(() -> chatService.createDiaryReference(user.getId(), session.getId(),
                new CreateDiaryReferenceRequest(concern.getId(), draft.getId())), DiaryErrorCode.DIARY_NOT_FOUND);
        assertErrorCode(() -> chatService.createDiaryReference(user.getId(), session.getId(),
                new CreateDiaryReferenceRequest(concern.getId(), others.getId())), DiaryErrorCode.DIARY_NOT_FOUND);
        verifyNoInteractions(diaryComparisonAiClient);
    }

    @Test
    void 종료된_대화에는_과거_일기를_연결할_수_없다() {
        Diary diary = savedDiary(user, LocalDate.of(2026, 9, 15), "발표를 앞둔 날", "본문");
        session.end(null, null, Instant.now());
        em.flush();

        assertErrorCode(() -> chatService.createDiaryReference(user.getId(), session.getId(),
                new CreateDiaryReferenceRequest(concern.getId(), diary.getId())), ChatErrorCode.SESSION_CLOSED);
        verifyNoInteractions(diaryComparisonAiClient);
    }

    @Test
    void 비교_답변을_만들지_못하면_AI_UNAVAILABLE이고_아무것도_저장하지_않는다() {
        Diary diary = savedDiary(user, LocalDate.of(2026, 9, 15), "발표를 앞둔 날", "본문");
        given(diaryComparisonAiClient.compare(any())).willThrow(new ChatAiException("실패", null));

        assertErrorCode(() -> chatService.createDiaryReference(user.getId(), session.getId(),
                new CreateDiaryReferenceRequest(concern.getId(), diary.getId())), ChatErrorCode.AI_UNAVAILABLE);
        em.clear();
        Long messageCount = em.getEntityManager()
                .createQuery("select count(m) from ChatMessage m where m.session.id = :id", Long.class)
                .setParameter("id", session.getId())
                .getSingleResult();
        assertThat(messageCount).isEqualTo(2);
    }

    @Test
    void 대화나_일기를_지우면_일기_참조도_함께_지워진다() {
        Diary kept = savedDiary(user, LocalDate.of(2026, 9, 15), "발표를 앞둔 날", "본문");
        Diary removed = savedDiary(user, LocalDate.of(2026, 9, 20), "또 다른 발표", "본문");
        given(diaryComparisonAiClient.compare(any())).willReturn("비교 답변");
        UUID firstReply = chatService.createDiaryReference(user.getId(), session.getId(),
                new CreateDiaryReferenceRequest(concern.getId(), kept.getId())).assistantMessage().id();
        UUID secondReply = chatService.createDiaryReference(user.getId(), session.getId(),
                new CreateDiaryReferenceRequest(concern.getId(), removed.getId())).assistantMessage().id();
        em.flush();
        em.clear();

        em.getEntityManager().createNativeQuery("delete from diaries where id = :id")
                .setParameter("id", removed.getId().toString())
                .executeUpdate();
        em.clear();
        assertThat(em.find(DiaryReference.class, new DiaryReference.Id(secondReply, removed.getId()))).isNull();
        assertThat(em.find(ChatMessage.class, secondReply)).isNotNull();

        chatService.deleteSession(user.getId(), session.getId());
        em.flush();
        em.clear();
        assertThat(em.find(DiaryReference.class, new DiaryReference.Id(firstReply, kept.getId()))).isNull();
        assertThat(em.find(Diary.class, kept.getId())).isNotNull();
    }

    private Diary savedDiary(User owner, LocalDate entryDate, String title, String content) {
        Diary diary = em.persist(new Diary(owner, entryDate, title, content, Instant.now()));
        em.flush();
        return diary;
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
