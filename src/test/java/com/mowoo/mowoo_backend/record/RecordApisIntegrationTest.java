package com.mowoo.mowoo_backend.record;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.character.repository.AiCharacterRepository;
import com.mowoo.mowoo_backend.chat.ai.ChatAiClient;
import com.mowoo.mowoo_backend.chat.ai.ChatSummaryAiClient;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.chat.repository.ChatSessionRepository;
import com.mowoo.mowoo_backend.diary.entity.Diary;
import com.mowoo.mowoo_backend.diary.entity.DiaryEmotion;
import com.mowoo.mowoo_backend.diary.repository.DiaryRepository;
import com.mowoo.mowoo_backend.emotion.entity.EmotionType;
import com.mowoo.mowoo_backend.login.client.KakaoClient;
import com.mowoo.mowoo_backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class RecordApisIntegrationTest {
    @DynamicPropertySource
    static void requireTestDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("DB_URL");
        if (url == null || !url.startsWith("jdbc:mysql://")
                || !URI.create(url.substring(5)).getPath().matches("/(?:test|test[_-].*|.*[_-]test|.*[_-]test[_-].*)")) {
            throw new IllegalStateException("회원·기록 삭제 테스트는 전용 MySQL 테스트 DB에서 실행하세요.");
        }
        registry.add("spring.datasource.url", () -> url);
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiCharacterRepository characters;
    @Autowired ChatSessionRepository sessions;
    @Autowired DiaryRepository diaries;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @MockitoBean KakaoClient kakao;
    @MockitoBean ChatAiClient chatAi;
    @MockitoBean ChatSummaryAiClient summaryAi;

    private Login owner;
    private Login other;

    @BeforeEach
    void createUsers() throws Exception {
        owner = kakaoLogin("10001");
        other = kakaoLogin("10002");
    }

    @Test
    void selectedDeletionIsAtomicAndKeepsConversations() throws Exception {
        ChatSession session = session(owner);
        Diary own = diary(owner, session);
        Diary foreign = diary(other, null);
        addEmotion(own);
        em.flush();
        mvc.perform(post("/api/v1/diaries/bulk-delete").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diaryIds\":[\"" + own.getId() + "\",\"" + foreign.getId() + "\"]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("DIARY_NOT_FOUND"));
        assertThat(diaries.existsById(own.getId())).isTrue();
        mvc.perform(post("/api/v1/diaries/bulk-delete").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diaryIds\":[\"" + own.getId() + "\",\"" + own.getId() + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("DIARIES_DELETE_SUCCESS"));
        assertThat(diaries.existsById(own.getId())).isFalse();
        assertThat(diaries.existsById(foreign.getId())).isTrue();
        assertThat(sessions.existsById(session.getId())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from diary_emotions where diary_id = ?",
                Integer.class, own.getId().toString())).isZero();
    }

    @Test
    void selectedDeletionValidatesItsBody() throws Exception {
        for (String body : new String[]{"{\"diaryIds\":[]}", "{\"diaryIds\":[null]}", "{}"}) {
            mvc.perform(post("/api/v1/diaries/bulk-delete").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void deleteRecordsKeepsAccountAndOwnershipAndCanBeRepeated() throws Exception {
        ChatSession ownSession = session(owner);
        ChatSession otherSession = session(other);
        Diary ownDiary = diary(owner, ownSession);
        Diary otherDiary = diary(other, otherSession);
        addEmotion(ownDiary);
        em.persist(ChatMessage.ofUser(ownSession, 1, "사용자 메시지"));
        em.persist(ChatMessage.ofAssistant(ownSession, 2, ownSession.getCharacter(), "AI 답변"));
        diaries.saveAndFlush(Diary.draft(users.getReferenceById(owner.id()), session(owner),
                LocalDate.of(2026, 11, 12), "초안", "아직 저장하지 않은 일기"));
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(delete("/api/v1/me/records").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("RECORDS_DELETE_SUCCESS"));
        }
        assertThat(jdbc.queryForObject("select count(*) from diaries where user_id = ?",
                Integer.class, owner.id().toString())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from chat_sessions where user_id = ?",
                Integer.class, owner.id().toString())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from chat_messages where session_id = ?",
                Integer.class, ownSession.getId().toString())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from diary_emotions where diary_id = ?",
                Integer.class, ownDiary.getId().toString())).isZero();
        assertThat(diaries.existsById(otherDiary.getId())).isTrue();
        assertThat(sessions.existsById(otherSession.getId())).isTrue();
        assertThat(users.existsById(owner.id())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from user_characters where user_id = ?", Integer.class, owner.id().toString())).isEqualTo(3);
        mvc.perform(post("/api/v1/auth/reissue").cookie(owner.refresh())).andExpect(status().isOk());
    }

    @Test
    void withdrawalDeletesAccountAndTokensAndExpiresCookie() throws Exception {
        ChatSession ownSession = session(owner);
        Diary ownDiary = diary(owner, ownSession);
        addEmotion(ownDiary);
        diary(other, session(other));
        em.flush();
        mvc.perform(delete("/api/v1/auth/withdraw").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("ACCOUNT_DELETE_SUCCESS"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        assertThat(users.existsById(owner.id())).isFalse();
        assertThat(users.existsById(other.id())).isTrue();
        assertThat(sessions.existsById(ownSession.getId())).isFalse();
        assertThat(diaries.existsById(ownDiary.getId())).isFalse();
        for (String table : new String[]{"refresh_tokens", "user_auth_identities", "user_characters"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?",
                    Integer.class, owner.id().toString())).isZero();
        }
        mvc.perform(post("/api/v1/auth/reissue").cookie(owner.refresh()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawalAlsoWorksWithoutAnyRecords() throws Exception {
        mvc.perform(delete("/api/v1/auth/withdraw").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("ACCOUNT_DELETE_SUCCESS"));
        assertThat(users.existsById(owner.id())).isFalse();
        assertThat(users.existsById(other.id())).isTrue();
    }

    @Test
    void allDeleteEndpointsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/diaries/bulk-delete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diaryIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/me/records")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/auth/withdraw")).andExpect(status().isUnauthorized());
    }

    @Test
    void allDeleteEndpointsRejectAUserWhoNoLongerExists() throws Exception {
        UUID missingUser = UUID.randomUUID();
        mvc.perform(post("/api/v1/diaries/bulk-delete")
                        .with(jwt().jwt(token -> token.subject(missingUser.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diaryIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/me/records")
                        .with(jwt().jwt(token -> token.subject(missingUser.toString()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/auth/withdraw")
                        .with(jwt().jwt(token -> token.subject(missingUser.toString()))))
                .andExpect(status().isUnauthorized());
        assertThat(users.existsById(owner.id())).isTrue();
        assertThat(users.existsById(other.id())).isTrue();
    }

    private Login kakaoLogin(String subject) throws Exception {
        when(kakao.getUser(subject)).thenReturn(new KakaoClient.KakaoUser(subject, "테스트 사용자", null, null));
        var response = mvc.perform(post("/api/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + subject + "\"}")).andExpect(status().isOk()).andReturn();
        var value = body(response);
        Cookie cookie = new Cookie("refresh_token", response.getResponse().getHeader(HttpHeaders.SET_COOKIE)
                .split(";", 2)[0].substring("refresh_token=".length()));
        return new Login(UUID.fromString(value.path("id").asString()),
                "Bearer " + value.path("accessToken").asString(), cookie);
    }

    private ChatSession session(Login login) {
        var character = characters.findByCodeInAndPremiumFalse(java.util.List.of("KIND")).getFirst();
        return sessions.saveAndFlush(new ChatSession(users.getReferenceById(login.id()), character));
    }

    private Diary diary(Login login, ChatSession session) {
        Diary diary = session == null
                ? new Diary(users.getReferenceById(login.id()), LocalDate.of(2026, 11, 12), "일기", "본문", Instant.now())
                : Diary.draft(users.getReferenceById(login.id()), session, LocalDate.of(2026, 11, 12), "일기", "본문");
        diary.update(null, null, null, true, Instant.now());
        return diaries.saveAndFlush(diary);
    }

    private void addEmotion(Diary diary) {
        var emotion = new EmotionType("TEST_" + UUID.randomUUID(), "테스트", "#112233", null);
        em.persist(emotion);
        em.persist(new DiaryEmotion(diary, emotion, true));
    }

    private JsonNode body(MvcResult result) {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private record Login(UUID id, String bearer, Cookie refresh) {
    }
}
