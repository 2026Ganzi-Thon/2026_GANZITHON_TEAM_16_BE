package com.mowoo.mowoo_backend.login;

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
import com.mowoo.mowoo_backend.login.client.GoogleClient;
import com.mowoo.mowoo_backend.login.client.NaverClient;
import com.mowoo.mowoo_backend.login.client.SocialUser;
import com.mowoo.mowoo_backend.login.domain.UserAuthIdentity.Provider;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class AuthProvidersIntegrationTest {
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
    @MockitoBean GoogleClient googleClient;
    @MockitoBean NaverClient naverClient;
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
    void providerIdentitiesRemainSeparateEvenWhenSubjectsAndEmailsMatch() throws Exception {
        when(googleClient.getUser("google-code"))
                .thenReturn(new SocialUser(Provider.GOOGLE, "same", "구글 사용자", "same@example.test", "https://images.example.test/google.png"));
        when(naverClient.getUser("naver-code", "verified-state"))
                .thenReturn(new SocialUser(Provider.NAVER, "same", "네이버 사용자", "same@example.test", "https://images.example.test/naver.png"));
        var google = body(mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"google-code\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LOGIN_SUCCESS")).andReturn());
        var naver = body(mvc.perform(post("/api/v1/auth/naver").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"naver-code\",\"state\":\"verified-state\"}")).andExpect(status().isOk()).andReturn());
        assertThat(google.path("id").asString()).isNotEqualTo(naver.path("id").asString());
        var googleUser = users.findById(UUID.fromString(google.path("id").asString())).orElseThrow();
        var naverUser = users.findById(UUID.fromString(naver.path("id").asString())).orElseThrow();
        assertThat(googleUser.getNickname()).isEqualTo("구글 사용자");
        assertThat(naverUser.getNickname()).isEqualTo("네이버 사용자");
        assertThat(googleUser.getEmail()).isEqualTo("same@example.test");
        assertThat(naverUser.getEmail()).isEqualTo("same@example.test");
        assertThat(googleUser.getProfileImageUrl()).isEqualTo("https://images.example.test/google.png");
        assertThat(naverUser.getProfileImageUrl()).isEqualTo("https://images.example.test/naver.png");
        assertThat(jdbc.queryForObject("select count(*) from user_characters where user_id = ?", Integer.class, google.path("id").asString())).isEqualTo(3);
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
