package com.mowoo.mowoo_backend.chat.service;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import com.mowoo.mowoo_backend.chat.ai.ChatAiClient;
import com.mowoo.mowoo_backend.chat.ai.ChatSummaryAiClient;
import com.mowoo.mowoo_backend.chat.ai.DiaryComparisonAiClient;
import com.mowoo.mowoo_backend.chat.ai.DiarySuggestionAiClient;
import com.mowoo.mowoo_backend.chat.ai.ChatAiException;
import com.mowoo.mowoo_backend.chat.ai.ChatAiRequest;
import com.mowoo.mowoo_backend.chat.dto.request.SendMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.request.UpdateMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.response.ChatMessageResponse;
import com.mowoo.mowoo_backend.chat.dto.response.SendMessageResponse;
import com.mowoo.mowoo_backend.chat.dto.response.UpdateMessageResponse;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import com.mowoo.mowoo_backend.chat.exception.ChatErrorCode;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.request.CursorRequest;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 로컬 MySQL에서 실행하고 테스트마다 롤백한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatService.class)
class ChatServiceMessageTest {

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

    @BeforeEach
    void setUp() {
        user = em.persist(new User("은송", null, null));
        character = em.persist(new AiCharacter(uniqueCode(), "다정한 모우", "따뜻하게 들어 주는 모우", null, false));
        em.persist(new UserCharacter(user, character));
        session = em.persist(new ChatSession(user, character));
        em.flush();
        given(chatAiClient.reply(any())).willReturn("어떤 점이 가장 걱정되나요?");
        given(chatSummaryAiClient.summarize(any()))
                .willReturn(new ChatSummaryAiClient.Summary("면접을 앞둔 날", "면접 걱정을 이야기했어요."));
    }

    @Test
    void 메시지를_보내면_사용자_메시지와_AI_응답을_이어서_저장한다() {
        SendMessageResponse response = send("오늘 면접이 걱정돼요.");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.code()).isEqualTo("CHAT_MESSAGE_SEND_SUCCESS");
        assertThat(response.userMessage().sequenceNo()).isEqualTo(1);
        assertThat(response.userMessage().senderRole()).isEqualTo(SenderRole.USER);
        assertThat(response.userMessage().characterId()).isNull();
        assertThat(response.userMessage().content()).isEqualTo("오늘 면접이 걱정돼요.");
        assertThat(response.assistantMessage().sequenceNo()).isEqualTo(2);
        assertThat(response.assistantMessage().senderRole()).isEqualTo(SenderRole.ASSISTANT);
        assertThat(response.assistantMessage().characterId()).isEqualTo(character.getId());
        assertThat(response.assistantMessage().content()).isEqualTo("어떤 점이 가장 걱정되나요?");
        assertThat(response.completedTurns()).isEqualTo(1);
        assertThat(response.canGenerateDiary()).isFalse();
    }

    @Test
    void AI에는_캐릭터와_이번_메시지까지의_대화_기록을_넘긴다() {
        send("오늘 면접이 걱정돼요.");
        send("말을 잘 못할까 봐요.");

        ArgumentCaptor<ChatAiRequest> captor = ArgumentCaptor.forClass(ChatAiRequest.class);
        verify(chatAiClient, times(2)).reply(captor.capture());
        ChatAiRequest second = captor.getAllValues().get(1);

        assertThat(second.characterName()).isEqualTo("다정한 모우");
        assertThat(second.characterDescription()).isEqualTo("따뜻하게 들어 주는 모우");
        assertThat(second.messages()).extracting(ChatAiRequest.Message::senderRole)
                .containsExactly(SenderRole.USER, SenderRole.ASSISTANT, SenderRole.USER);
        assertThat(second.messages().getLast().content()).isEqualTo("말을 잘 못할까 봐요.");
    }

    @Test
    void 다섯_번째_왕복부터_AI_일기를_만들_수_있다() {
        for (int turn = 1; turn <= 4; turn++) {
            assertThat(send("메시지 " + turn).canGenerateDiary()).isFalse();
        }

        SendMessageResponse fifth = send("메시지 5");

        assertThat(fifth.completedTurns()).isEqualTo(5);
        assertThat(fifth.canGenerateDiary()).isTrue();
    }

    @Test
    void 메시지_응답의_createdAt은_이후_조회와_같다() {
        SendMessageResponse response = send("오늘 면접이 걱정돼요.");
        em.clear();

        List<ChatMessageResponse> items = chatService.getSession(
                user.getId(), session.getId(), new CursorRequest(null, null)).messages().items();

        assertThat(items.get(0).createdAt()).isEqualTo(response.userMessage().createdAt());
        assertThat(items.get(1).createdAt()).isEqualTo(response.assistantMessage().createdAt());
    }

    @Test
    void 보유한_다른_캐릭터를_지정하면_그_캐릭터로_응답한다() {
        AiCharacter other = em.persist(new AiCharacter(uniqueCode(), "냉철한 모우", null, null, false));
        em.persist(new UserCharacter(user, other));

        SendMessageResponse response = chatService.sendMessage(
                user.getId(), session.getId(), new SendMessageRequest("어떻게 하면 좋을까?", other.getId()));

        assertThat(response.assistantMessage().characterId()).isEqualTo(other.getId());
    }

    @Test
    void 보유하지_않은_캐릭터를_지정하면_CHARACTER_NOT_OWNED다() {
        AiCharacter premium = em.persist(new AiCharacter(uniqueCode(), "퀸 마인드 모우", null, null, true));

        assertErrorCode(() -> chatService.sendMessage(user.getId(), session.getId(),
                        new SendMessageRequest("안녕", premium.getId())),
                ChatErrorCode.CHARACTER_NOT_OWNED);
    }

    @Test
    void 본문이_비어_있으면_MESSAGE_EMPTY다() {
        assertErrorCode(() -> chatService.sendMessage(user.getId(), session.getId(),
                        new SendMessageRequest("   ", null)),
                ChatErrorCode.MESSAGE_EMPTY);
        verifyNoInteractions(chatAiClient);
    }

    @Test
    void 종료된_대화에는_메시지를_보낼_수_없다() {
        chatService.endSession(user.getId(), session.getId());

        assertErrorCode(() -> send("안녕"), ChatErrorCode.SESSION_CLOSED);
    }

    @Test
    void 다른_사용자의_대화에는_메시지를_보낼_수_없다() {
        User otherUser = em.persist(new User("다른 사용자", null, null));

        assertErrorCode(() -> chatService.sendMessage(otherUser.getId(), session.getId(),
                        new SendMessageRequest("안녕", null)),
                ChatErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void AI가_실패하면_AI_UNAVAILABLE이다() {
        given(chatAiClient.reply(any())).willThrow(new ChatAiException("시간 초과", null));

        assertErrorCode(() -> send("안녕"), ChatErrorCode.AI_UNAVAILABLE);
    }

    @Test
    void 최근_사용자_메시지를_수정해도_AI_응답은_다시_만들지_않는다() {
        SendMessageResponse sent = send("오늘 면접이 걱정돼요.");

        UpdateMessageResponse response = chatService.updateMessage(
                user.getId(), session.getId(), sent.userMessage().id(), new UpdateMessageRequest("오늘 발표가 걱정돼요."));

        assertThat(response.code()).isEqualTo("CHAT_MESSAGE_UPDATE_SUCCESS");
        assertThat(response.userMessage().id()).isEqualTo(sent.userMessage().id());
        assertThat(response.userMessage().content()).isEqualTo("오늘 발표가 걱정돼요.");
        assertThat(response.userMessage().editedAt()).isNotNull();
        assertThat(response.assistantMessage()).isEqualTo(sent.assistantMessage());
        // 메시지 전송 때 한 번만 불렸다.
        verify(chatAiClient, times(1)).reply(any());
    }

    @Test
    void 수정한_내용은_다음_AI_응답의_대화_기록에_들어간다() {
        SendMessageResponse sent = send("오늘 면접이 걱정돼요.");
        chatService.updateMessage(
                user.getId(), session.getId(), sent.userMessage().id(), new UpdateMessageRequest("오늘 발표가 걱정돼요."));

        send("말을 잘 못할까 봐요.");

        ArgumentCaptor<ChatAiRequest> captor = ArgumentCaptor.forClass(ChatAiRequest.class);
        verify(chatAiClient, times(2)).reply(captor.capture());
        assertThat(captor.getValue().messages()).extracting(ChatAiRequest.Message::content)
                .containsExactly("오늘 발표가 걱정돼요.", "어떤 점이 가장 걱정되나요?", "말을 잘 못할까 봐요.");
    }

    @Test
    void 최근이_아닌_사용자_메시지는_수정할_수_없다() {
        SendMessageResponse first = send("첫 메시지");
        send("두 번째 메시지");

        assertErrorCode(() -> chatService.updateMessage(user.getId(), session.getId(), first.userMessage().id(),
                new UpdateMessageRequest("수정")), ChatErrorCode.MESSAGE_NOT_EDITABLE);
    }

    @Test
    void AI_응답은_수정할_수_없다() {
        SendMessageResponse sent = send("오늘 면접이 걱정돼요.");

        assertErrorCode(() -> chatService.updateMessage(user.getId(), session.getId(), sent.assistantMessage().id(),
                new UpdateMessageRequest("수정")), ChatErrorCode.MESSAGE_NOT_EDITABLE);
    }

    @Test
    void 없는_메시지나_다른_사용자의_메시지는_MESSAGE_NOT_FOUND다() {
        SendMessageResponse sent = send("오늘 면접이 걱정돼요.");
        User otherUser = em.persist(new User("다른 사용자", null, null));

        assertErrorCode(() -> chatService.updateMessage(user.getId(), session.getId(), UUID.randomUUID(),
                new UpdateMessageRequest("수정")), ChatErrorCode.MESSAGE_NOT_FOUND);
        assertErrorCode(() -> chatService.updateMessage(otherUser.getId(), session.getId(), sent.userMessage().id(),
                new UpdateMessageRequest("수정")), ChatErrorCode.MESSAGE_NOT_FOUND);
    }

    @Test
    void 종료된_대화의_메시지는_수정할_수_없다() {
        SendMessageResponse sent = send("오늘 면접이 걱정돼요.");
        chatService.endSession(user.getId(), session.getId());

        assertErrorCode(() -> chatService.updateMessage(user.getId(), session.getId(), sent.userMessage().id(),
                new UpdateMessageRequest("수정")), ChatErrorCode.SESSION_CLOSED);
    }

    private SendMessageResponse send(String content) {
        return chatService.sendMessage(user.getId(), session.getId(), new SendMessageRequest(content, null));
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
