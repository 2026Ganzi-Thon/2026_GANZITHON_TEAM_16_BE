package com.mowoo.mowoo_backend.diary;

import com.mowoo.mowoo_backend.ai.AiUnavailableException;
import com.mowoo.mowoo_backend.diary.ai.DiaryAiClient;
import com.mowoo.mowoo_backend.diary.ai.DiaryAiResult;
import com.mowoo.mowoo_backend.diary.service.DiaryDraftService;
import com.mowoo.mowoo_backend.emotion.ai.EmotionAiClient;
import com.mowoo.mowoo_backend.emotion.ai.EmotionAiResult;
import com.mowoo.mowoo_backend.calendar.ai.MonthlyRecapAiClient;
import com.mowoo.mowoo_backend.login.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "mowoo.emotion.seed.enabled=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class DiaryAiIntegrationTest extends DiaryDatabaseTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtProvider jwt;
    @Autowired private JsonMapper json;
    @Autowired private DiaryDraftService drafts;
    @MockitoBean private DiaryAiClient diaryAi;
    @MockitoBean private EmotionAiClient emotionAi;
    @MockitoBean private MonthlyRecapAiClient recapAi;
    private UUID user;
    private UUID character;
    private UUID session;

    @BeforeEach
    void setUp() {
        user = UUID.randomUUID(); character = UUID.randomUUID(); session = UUID.randomUUID();
        jdbc.update("insert into users (id, nickname) values (?, 'ai-test')", user.toString());
        jdbc.update("insert into ai_characters (id, code, name) values (?, ?, 'ai-test')",
                character.toString(), "ai-test-" + character);
        jdbc.update("insert into chat_sessions (id, user_id, character_id) values (?, ?, ?)",
                session.toString(), user.toString(), character.toString());
        for (int i = 1; i <= 10; i++) {
            jdbc.update("insert into chat_messages (id, session_id, sequence_no, sender_role, character_id, content) "
                            + "values (?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), session.toString(), i,
                    i % 2 == 1 ? "USER" : "ASSISTANT", i % 2 == 1 ? null : character.toString(),
                    i % 2 == 1 ? "오늘 산책했다." : "산책 이야기를 들었어요.");
        }
        given(diaryAi.generate(any())).willReturn(new DiaryAiResult("산책한 날", "오늘 산책했다."));
        given(emotionAi.suggest(any(), any())).willReturn(new EmotionAiResult(List.of("CALM"), "CALM"));
        given(recapAi.summarize(any())).willReturn("산책에 관한 기록이 있었다.");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from diaries where user_id = ?", user.toString());
        jdbc.update("delete from chat_sessions where id = ?", session.toString());
        jdbc.update("delete from users where id = ?", user.toString());
        jdbc.update("delete from ai_characters where id = ?", character.toString());
    }

    @Test
    void generatesOneDraftAndReusesItWithoutCallingAiAgain() throws Exception {
        String first = generate();
        assertThat(generate()).isEqualTo(first);
        verify(diaryAi, times(1)).generate(any());
        assertThat(jdbc.queryForObject("select count(*) from diaries where source_session_id = ?",
                Integer.class, session.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select saved_at from diaries where id = ?", Object.class, first)).isNull();
    }

    @Test
    void fewerThanFiveTurnsAndOtherUsersAreRejectedBeforeAiCall() throws Exception {
        jdbc.update("delete from chat_messages where session_id = ? and sequence_no > 8", session.toString());
        mvc.perform(put(path()).header("Authorization", token(user)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MIN_CHAT_TURNS"));
        mvc.perform(put(path()).header("Authorization", token(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        verifyNoInteractions(diaryAi);
    }

    @Test
    void invalidOrFailedAiOutputIsNeverSaved() throws Exception {
        given(diaryAi.generate(any())).willReturn(new DiaryAiResult("", ""));
        mvc.perform(put(path()).header("Authorization", token(user)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
        given(diaryAi.generate(any())).willThrow(new AiUnavailableException());
        mvc.perform(put(path()).header("Authorization", token(user)))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select count(*) from diaries where user_id = ?",
                Integer.class, user.toString())).isZero();
    }

    @Test
    void concurrentGenerationKeepsOneRowWithoutHoldingDatabaseLockDuringAiCall() throws Exception {
        CountDownLatch bothInAi = new CountDownLatch(2);
        given(diaryAi.generate(any())).willAnswer(invocation -> {
            bothInAi.countDown();
            assertThat(bothInAi.await(10, TimeUnit.SECONDS)).isTrue();
            return new DiaryAiResult("산책한 날", "오늘 산책했다.");
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> drafts.generate(user, session, LocalDate.of(2026, 11, 11)));
            var second = workers.submit(() -> drafts.generate(user, session, LocalDate.of(2026, 11, 11)));
            assertThat(first.get(20, TimeUnit.SECONDS).id()).isEqualTo(second.get(20, TimeUnit.SECONDS).id());
        }
        assertThat(jdbc.queryForObject("select count(*) from diaries where source_session_id = ?",
                Integer.class, session.toString())).isEqualTo(1);
    }

    @Test
    void recommendationDoesNotConfirmEmotionsAndMonthlyRecapUsesSavedData() throws Exception {
        String id = generate();
        mvc.perform(post("/api/v1/diaries/{id}/emotion-suggestions", id).header("Authorization", token(user)))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/diaries/{id}", id).header("Authorization", token(user))
                        .contentType("application/json").content("{\"save\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/diaries/{id}/emotion-suggestions", id).header("Authorization", token(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.suggestedEmotionIds.length()").value(1));
        assertThat(jdbc.queryForObject("select count(*) from diary_emotions where diary_id = ?", Integer.class, id)).isZero();
        mvc.perform(get("/api/v1/calendar/recap").param("month", "2026-11").header("Authorization", token(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.concernSummary").value("산책에 관한 기록이 있었다."));
        mvc.perform(get("/api/v1/calendar/recap").param("month", "2026-10").header("Authorization", token(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.concernSummary").isEmpty());
        verify(recapAi, times(1)).summarize(any());
    }

    @Test
    void rejectsUnknownOrDuplicateRecommendedCodes() throws Exception {
        String id = generate();
        jdbc.update("update diaries set saved_at = current_timestamp where id = ?", id);
        given(emotionAi.suggest(any(), any())).willReturn(new EmotionAiResult(List.of("UNKNOWN"), "UNKNOWN"));
        mvc.perform(post("/api/v1/diaries/{id}/emotion-suggestions", id).header("Authorization", token(user)))
                .andExpect(status().isServiceUnavailable());
        given(emotionAi.suggest(any(), any())).willReturn(new EmotionAiResult(List.of("CALM", "CALM"), "CALM"));
        mvc.perform(post("/api/v1/diaries/{id}/emotion-suggestions", id).header("Authorization", token(user)))
                .andExpect(status().isServiceUnavailable());
    }

    private String generate() throws Exception {
        String response = mvc.perform(put(path()).param("entryDate", "2026-11-11").header("Authorization", token(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.savedAt").isEmpty())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("id").asString();
    }
    private String path() { return "/api/v1/chat-sessions/" + session + "/diary-draft"; }
    private String token(UUID id) { return "Bearer " + jwt.createAccessToken(id, "test"); }
}
