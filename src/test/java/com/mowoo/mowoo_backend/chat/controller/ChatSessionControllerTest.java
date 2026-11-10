package com.mowoo.mowoo_backend.chat.controller;

import com.mowoo.mowoo_backend.chat.dto.request.CreateDiaryReferenceRequest;
import com.mowoo.mowoo_backend.chat.dto.request.SendMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.request.StartChatSessionRequest;
import com.mowoo.mowoo_backend.chat.dto.request.UpdateMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.response.ChatMessagePage;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionDetailResponse;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionItem;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionResponse;
import com.mowoo.mowoo_backend.chat.dto.response.CreateDiaryReferenceResponse;
import com.mowoo.mowoo_backend.chat.dto.response.SendMessageResponse;
import com.mowoo.mowoo_backend.chat.dto.response.UpdateMessageResponse;
import com.mowoo.mowoo_backend.chat.service.ChatService;
import com.mowoo.mowoo_backend.global.config.JwtConfig;
import com.mowoo.mowoo_backend.global.config.SecurityConfig;
import com.mowoo.mowoo_backend.global.exception.SecurityErrorHandler;
import com.mowoo.mowoo_backend.global.request.CursorRequest;
import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import com.mowoo.mowoo_backend.global.response.CursorResponse;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatSessionController.class)
@Import({SecurityConfig.class, JwtConfig.class, SecurityErrorHandler.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-key-for-mowoo-backend-0123456789")
class ChatSessionControllerTest {

    private static final UUID USER_ID = UUID.fromString("a3c8bfe1-0b2a-4891-88c5-d4af752c0001");
    private static final UUID SESSION_ID = UUID.fromString("a3c8bfe1-0b2a-4891-88c5-d4af752c0011");
    private static final UUID CHARACTER_ID = UUID.fromString("a3c8bfe1-0b2a-4891-88c5-d4af752c0022");
    private static final UUID MESSAGE_ID = UUID.fromString("a3c8bfe1-0b2a-4891-88c5-d4af752c0033");
    private static final UUID DIARY_ID = UUID.fromString("a3c8bfe1-0b2a-4891-88c5-d4af752c0044");
    private static final ChatSessionItem SESSION_ITEM = new ChatSessionItem(
            SESSION_ID, CHARACTER_ID, null, null, Instant.parse("2026-11-07T12:00:00Z"), null, 0, false);
    private static final ChatSessionResponse SESSION =
            ChatSessionResponse.of(CommonSuccessCode.CHAT_SESSION_START_SUCCESS, SESSION_ITEM);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    void startSessionReturnsCreatedSession() throws Exception {
        given(chatService.startSession(eq(USER_ID), any())).willReturn(SESSION);

        mockMvc.perform(post("/api/v1/chat-sessions").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterId\":\"" + CHARACTER_ID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("CHAT_SESSION_START_SUCCESS"))
                .andExpect(jsonPath("$.id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.completedTurns").value(0));

        verify(chatService).startSession(USER_ID, new StartChatSessionRequest(CHARACTER_ID));
    }

    @Test
    void startSessionWithoutCharacterIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/chat-sessions").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(chatService);
    }

    @Test
    void sessionListUsesDefaultLimit() throws Exception {
        given(chatService.getSessions(eq(USER_ID), any())).willReturn(new CursorResponse<>(
                CommonSuccessCode.CHAT_SESSION_LIST_READ_SUCCESS, List.of(SESSION_ITEM), null));

        mockMvc.perform(get("/api/v1/chat-sessions").with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        ArgumentCaptor<CursorRequest> captor = ArgumentCaptor.forClass(CursorRequest.class);
        verify(chatService).getSessions(eq(USER_ID), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CursorRequest(20, null));
    }

    @Test
    void sessionListRejectsLimitOver100() throws Exception {
        mockMvc.perform(get("/api/v1/chat-sessions").with(loginUser()).param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(chatService);
    }

    @Test
    void sessionDetailPassesCursor() throws Exception {
        given(chatService.getSession(eq(USER_ID), eq(SESSION_ID), any()))
                .willReturn(new ChatSessionDetailResponse(SESSION_ITEM, new ChatMessagePage(List.of(), null)));

        mockMvc.perform(get("/api/v1/chat-sessions/{sessionId}", SESSION_ID).with(loginUser())
                        .param("limit", "50")
                        .param("cursor", "opaque_cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.messages.items").isEmpty());

        verify(chatService).getSession(USER_ID, SESSION_ID, new CursorRequest(50, "opaque_cursor"));
    }

    @Test
    void endActionEndsSession() throws Exception {
        given(chatService.endSession(USER_ID, SESSION_ID)).willReturn(SESSION);

        mockMvc.perform(patch("/api/v1/chat-sessions/{sessionId}", SESSION_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"END\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID.toString()));
    }

    @Test
    void unknownActionIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/chat-sessions/{sessionId}", SESSION_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"STOP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(chatService);
    }

    @Test
    void deleteSessionReturnsSuccessResponse() throws Exception {
        mockMvc.perform(delete("/api/v1/chat-sessions/{sessionId}", SESSION_ID).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("CHAT_SESSION_DELETE_SUCCESS"))
                .andExpect(jsonPath("$.message").value("대화를 삭제했어요."));

        verify(chatService).deleteSession(USER_ID, SESSION_ID);
    }

    @Test
    void messageIsSent() throws Exception {
        given(chatService.sendMessage(eq(USER_ID), eq(SESSION_ID), any()))
                .willReturn(new SendMessageResponse(null, null, 1, false));

        mockMvc.perform(post("/api/v1/chat-sessions/{sessionId}/messages", SESSION_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"오늘 면접이 걱정돼요.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.completedTurns").value(1));

        verify(chatService).sendMessage(USER_ID, SESSION_ID, new SendMessageRequest("오늘 면접이 걱정돼요.", null));
    }

    @Test
    void updateMessagePassesContent() throws Exception {
        given(chatService.updateMessage(eq(USER_ID), eq(SESSION_ID), eq(MESSAGE_ID), any()))
                .willReturn(new UpdateMessageResponse(null, null));

        mockMvc.perform(patch("/api/v1/chat-sessions/{sessionId}/messages/{messageId}", SESSION_ID, MESSAGE_ID)
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"오늘 발표가 걱정돼요.\"}"))
                .andExpect(status().isOk());

        verify(chatService).updateMessage(USER_ID, SESSION_ID, MESSAGE_ID, new UpdateMessageRequest("오늘 발표가 걱정돼요."));
    }

    @Test
    void blankUpdatedMessageIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/chat-sessions/{sessionId}/messages/{messageId}", SESSION_ID, MESSAGE_ID)
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void diarySuggestionsRequireMessageId() throws Exception {
        mockMvc.perform(get("/api/v1/chat-sessions/{sessionId}/diary-suggestions", SESSION_ID).with(loginUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        given(chatService.getDiarySuggestions(USER_ID, SESSION_ID, MESSAGE_ID)).willReturn(new ListResponse<>(List.of()));

        mockMvc.perform(get("/api/v1/chat-sessions/{sessionId}/diary-suggestions", SESSION_ID).with(loginUser())
                        .param("messageId", MESSAGE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void diaryReferenceReturnsCreated() throws Exception {
        given(chatService.createDiaryReference(eq(USER_ID), eq(SESSION_ID), any()))
                .willReturn(new CreateDiaryReferenceResponse(null));

        mockMvc.perform(post("/api/v1/chat-sessions/{sessionId}/diary-references", SESSION_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":\"" + MESSAGE_ID + "\",\"diaryId\":\"" + DIARY_ID + "\"}"))
                .andExpect(status().isCreated());

        verify(chatService).createDiaryReference(USER_ID, SESSION_ID, new CreateDiaryReferenceRequest(MESSAGE_ID, DIARY_ID));
    }

    @Test
    void tokenWithoutUserIdIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/chat-sessions").with(jwt().jwt(token -> token.subject("not-a-uuid"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(chatService);
    }

    private RequestPostProcessor loginUser() {
        return jwt().jwt(token -> token.subject(USER_ID.toString()));
    }
}
