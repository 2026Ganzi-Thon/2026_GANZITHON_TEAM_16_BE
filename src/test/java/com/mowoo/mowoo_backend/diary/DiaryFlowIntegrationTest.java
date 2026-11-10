package com.mowoo.mowoo_backend.diary;

import com.mowoo.mowoo_backend.login.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 보안 필터와 서비스, MySQL을 통과한다. 실행 시 격리된 테스트 DB를 지정한다.
// 실패해도 인증 헤더나 일기 본문이 테스트 로그에 출력되지 않도록 요청 출력은 끈다.
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class DiaryFlowIntegrationTest extends DiaryDatabaseTest {

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> UUID.randomUUID().toString());
        registry.add("kakao.client-id", () -> "unused-test-client");
        registry.add("kakao.client-secret", () -> "unused-test-client");
        registry.add("kakao.redirect-uri", () -> "http://localhost/unused-test-callback");
        registry.add("cloud.aws.s3.enabled", () -> "false");
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JsonMapper jsonMapper;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void createUsers() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        jdbc.update("insert into users (id, nickname) values (?, ?)", userId.toString(), "diary-test");
        jdbc.update("insert into users (id, nickname) values (?, ?)", otherUserId.toString(), "diary-other-test");
    }

    @AfterEach
    void cleanUp() {
        if (jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema = database() and table_name = 'diaries'", Integer.class) > 0) {
            jdbc.update("delete from diaries where user_id in (?, ?)", userId.toString(), otherUserId.toString());
        }
        jdbc.update("delete from users where id in (?, ?)", userId.toString(), otherUserId.toString());
    }

    @Test
    void createsSavedDiaryAndReadsCommittedData() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        String id = createDiary();
        mvc.perform(get("/api/v1/diaries/{id}", id).header("Authorization", accessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("요청이 처리됐어요."))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.entryDate").value("2026-11-11"))
                .andExpect(jsonPath("$.title").value("오늘의 기록"))
                .andExpect(jsonPath("$.content").value("오늘 프로젝트를 시작했다."))
                .andExpect(jsonPath("$.sourceSessionId").isEmpty())
                .andExpect(jsonPath("$.emotionConfirmedAt").isEmpty())
                .andExpect(jsonPath("$.emotions").isArray())
                .andExpect(jsonPath("$.emotions").isEmpty())
                .andExpect(jsonPath("$.attachments").isArray())
                .andExpect(jsonPath("$.attachments").isEmpty());
        var row = jdbc.queryForMap("select user_id, saved_at, source_session_id from diaries where id = ?", id);
        assertThat(row.get("user_id")).isEqualTo(userId.toString());
        assertThat(row.get("saved_at")).isNotNull();
        assertThat(row.get("source_session_id")).isNull();
        assertThat(jdbc.queryForObject("select saved_at from diaries where id = ?", java.sql.Timestamp.class, id)
                .toInstant()).isAfterOrEqualTo(before);
    }

    @Test
    void anotherUserCannotReadDiary() throws Exception {
        String id = createDiary();
        mvc.perform(get("/api/v1/diaries/{id}", id).header("Authorization", accessToken(otherUserId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("DIARY_NOT_FOUND"));
    }

    @Test
    void absentDiaryHasSameErrorAsOtherUsersDiary() throws Exception {
        mvc.perform(get("/api/v1/diaries/{id}", UUID.randomUUID())
                        .header("Authorization", accessToken(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DIARY_NOT_FOUND"));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/diaries").contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/diaries/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenCannotCreateDiary() throws Exception {
        mvc.perform(post("/api/v1/diaries")
                        .header("Authorization", "Bearer " + jwtProvider.createRefreshToken(userId).getTokenValue())
                        .contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureCannotCreateDiary() throws Exception {
        String token = accessToken(userId);
        // 서명 첫 문자를 바꿔 유효하지 않은 토큰을 만든다. 토큰은 로그에 남기지 않는다.
        int start = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(start) == 'A' ? 'B' : 'A';
        String invalid = token.substring(0, start) + replacement + token.substring(start + 1);
        mvc.perform(post("/api/v1/diaries").header("Authorization", invalid)
                        .contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRequestsDoNotWriteDiary() throws Exception {
        String[] invalid = {
                "{}",
                """
                {"entryDate":"2026-11-11","title":" ","content":"text"}
                """,
                """
                {"entryDate":"2026-11-11","title":"title","content":" "}
                """,
                """
                {"entryDate":"2026-02-30","title":"title","content":"text"}
                """
        };
        for (String body : invalid) {
            mvc.perform(post("/api/v1/diaries").header("Authorization", accessToken(userId))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        assertThat(jdbc.queryForObject("select count(*) from diaries where user_id = ?",
                Integer.class, userId.toString())).isZero();
    }

    @Test
    void savedListUsesStableCursorAndExcludesDraftsAndOtherUsers() throws Exception {
        String first = createDiary();
        String second = createDiary();
        String draft = createDiary();
        jdbc.update("update diaries set saved_at = null where id = ?", draft);
        jdbc.update("insert into diaries (id, user_id, entry_date, title, content, saved_at) "
                        + "values (?, ?, '2026-11-11', 'other', 'other', current_timestamp)",
                UUID.randomUUID().toString(), otherUserId.toString());
        var expected = java.util.stream.Stream.of(first, second).sorted(java.util.Comparator.reverseOrder()).toList();
        assertThat(jdbc.queryForList("select id from diaries where user_id = ? and saved_at is not null "
                        + "order by entry_date desc, id desc", String.class, userId.toString()))
                .containsExactlyElementsOf(expected);
        String page = mvc.perform(get("/api/v1/diaries").header("Authorization", accessToken(userId))
                        .param("limit", "1").param("from", "2026-11-11").param("to", "2026-11-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(expected.getFirst()))
                .andExpect(jsonPath("$.items[0].isSuccess").doesNotExist())
                .andExpect(jsonPath("$.items[0].code").doesNotExist())
                .andExpect(jsonPath("$.items[0].message").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = jsonMapper.readTree(page).path("nextCursor").asString();
        mvc.perform(get("/api/v1/diaries").header("Authorization", accessToken(userId))
                        .param("limit", "1").param("from", "2026-11-11").param("to", "2026-11-11")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(expected.getLast()))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void rejectsInvalidListRangeLimitAndCursor() throws Exception {
        mvc.perform(get("/api/v1/diaries").header("Authorization", accessToken(userId))
                        .param("from", "2026-11-12").param("to", "2026-11-11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"));
        mvc.perform(get("/api/v1/diaries").header("Authorization", accessToken(userId)).param("limit", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/diaries").header("Authorization", accessToken(userId)).param("cursor", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void patchesDraftWithoutLosingFieldsAndSavesOnlyWhenRequested() throws Exception {
        String id = createDiary();
        jdbc.update("update diaries set saved_at = null where id = ?", id);
        mvc.perform(patch("/api/v1/diaries/{id}", id).header("Authorization", accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"수정한 제목\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정한 제목"))
                .andExpect(jsonPath("$.content").value("오늘 프로젝트를 시작했다."))
                .andExpect(jsonPath("$.savedAt").isEmpty());
        String saved = mvc.perform(patch("/api/v1/diaries/{id}", id).header("Authorization", accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"save\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.savedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String savedAt = jsonMapper.readTree(saved).path("savedAt").asString();
        mvc.perform(patch("/api/v1/diaries/{id}", id).header("Authorization", accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"save\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.savedAt").value(savedAt));
        jdbc.update("update diaries set emotion_confirmed_at = current_timestamp where id = ?", id);
        mvc.perform(patch("/api/v1/diaries/{id}", id).header("Authorization", accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"바뀐 본문\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emotionConfirmedAt").isEmpty());
        assertThat(jdbc.queryForObject("select content from diaries where id = ?", String.class, id))
                .isEqualTo("바뀐 본문");
    }

    @Test
    void anotherUserCannotPatchDiaryAndNullFieldsAreRejected() throws Exception {
        String id = createDiary();
        mvc.perform(patch("/api/v1/diaries/{id}", id).header("Authorization", accessToken(otherUserId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"other\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("DIARY_NOT_FOUND"));
        for (String field : java.util.List.of("title", "content", "entryDate", "save")) {
            mvc.perform(patch("/api/v1/diaries/{id}", id).header("Authorization", accessToken(userId))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"" + field + "\":null}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select title from diaries where id = ?", String.class, id))
                .isEqualTo("오늘의 기록");
    }

    @Test
    void deletedUserCannotCreateDiary() throws Exception {
        mvc.perform(post("/api/v1/diaries").header("Authorization", accessToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void bodyCannotOverrideAuthenticatedOwner() throws Exception {
        String body = payload().replace("{", "{\"userId\":\"" + otherUserId + "\",");
        String response = mvc.perform(post("/api/v1/diaries").header("Authorization", accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = jsonMapper.readTree(response).path("id").asString();
        assertThat(jdbc.queryForObject("select user_id from diaries where id = ?", String.class, id))
                .isEqualTo(userId.toString());
    }

    private String createDiary() throws Exception {
        String body = mvc.perform(post("/api/v1/diaries").header("Authorization", accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.savedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).path("id").asString();
    }

    private String payload() {
        return "{\"entryDate\":\"2026-11-11\",\"title\":\"오늘의 기록\",\"content\":\"오늘 프로젝트를 시작했다.\"}";
    }

    private String accessToken(UUID id) {
        return "Bearer " + jwtProvider.createAccessToken(id, "test-session");
    }
}
