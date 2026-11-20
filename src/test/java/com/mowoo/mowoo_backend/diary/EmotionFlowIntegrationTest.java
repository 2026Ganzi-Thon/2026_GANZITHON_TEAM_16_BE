package com.mowoo.mowoo_backend.diary;

import com.mowoo.mowoo_backend.login.jwt.JwtProvider;
import com.mowoo.mowoo_backend.emotion.service.DevelopmentEmotionSeeder;
import com.mowoo.mowoo_backend.diary.service.DiaryEmotionService;
import com.mowoo.mowoo_backend.diary.dto.request.ReplaceDiaryEmotionsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "mowoo.emotion.seed.enabled=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class EmotionFlowIntegrationTest extends DiaryDatabaseTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtProvider jwt;
    @Autowired private JsonMapper mapper;
    @Autowired private DevelopmentEmotionSeeder seeder;
    @Autowired private DiaryEmotionService emotionService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("insert into users (id, nickname) values (?, ?)", userId.toString(), "emotion-test");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from diaries where user_id = ?", userId.toString());
        jdbc.update("delete from users where id = ?", userId.toString());
    }

    @Test
    void listsDevelopmentEmotionsAndConfirmsThenReplacesSelection() throws Exception {
        Map<String, String> ids = emotionIds();
        assertThat(ids.keySet()).containsExactlyInAnyOrder("CALM", "ANGRY", "SAD", "HAPPY");
        String diary = diary();
        mvc.perform(put("/api/v1/diaries/{id}/emotions", diary).header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content(selection(ids.get("CALM"), ids.get("HAPPY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emotionConfirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.emotions.length()").value(2));
        mvc.perform(put("/api/v1/diaries/{id}/emotions", diary).header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content(selection(ids.get("SAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emotions.length()").value(1))
                .andExpect(jsonPath("$.emotions[0].code").value("SAD"))
                .andExpect(jsonPath("$.emotions[0].isPrimary").value(true));
        assertThat(jdbc.queryForObject("select count(*) from diary_emotions where diary_id = ?",
                Integer.class, diary)).isEqualTo(1);
        mvc.perform(get("/api/v1/diaries/{id}", diary).header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emotions[0].code").value("SAD"));
    }

    @Test
    void rejectsInvalidEmotionsWithoutChangingConfirmedSelection() throws Exception {
        var ids = emotionIds();
        String diary = diary();
        mvc.perform(put("/api/v1/diaries/{id}/emotions", diary).header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content(selection(ids.get("CALM"))))
                .andExpect(status().isOk());
        String[] invalid = {"{\"emotionIds\":[],\"primaryEmotionId\":\"" + ids.get("CALM") + "\"}",
                selection(UUID.randomUUID().toString()), selection(ids.get("CALM"), ids.get("CALM")),
                "{\"emotionIds\":[\"" + ids.get("CALM") + "\"],\"primaryEmotionId\":\"" + ids.get("SAD") + "\"}"};
        for (String body : invalid) {
            mvc.perform(put("/api/v1/diaries/{id}/emotions", diary).header("Authorization", token())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_EMOTIONS"));
        }
        assertThat(jdbc.queryForObject("select count(*) from diary_emotions where diary_id = ? and is_primary",
                Integer.class, diary)).isEqualTo(1);
    }

    @Test
    void draftAndOtherUsersDiaryCannotBeConfirmed() throws Exception {
        String id = diary();
        String body = selection(emotionIds().get("CALM"));
        mvc.perform(put("/api/v1/diaries/{id}/emotions", id)
                        .header("Authorization", "Bearer " + jwt.createAccessToken(UUID.randomUUID(), "test"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        jdbc.update("update diaries set saved_at = null where id = ?", id);
        mvc.perform(put("/api/v1/diaries/{id}/emotions", id).header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DIARY_NOT_SAVED"));
    }

    @Test
    void calendarReflectsConfirmationAndBodyEditInvalidatesIt() throws Exception {
        String id = diary();
        mvc.perform(put("/api/v1/diaries/{id}/emotions", id).header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content(selection(emotionIds().get("CALM"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/calendar").param("month", "2026-11").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].primaryEmotion.code").value("CALM"))
                .andExpect(jsonPath("$.days[0].diaryIds[0]").value(id));
        mvc.perform(patch("/api/v1/diaries/{id}", id).header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"본문 변경\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emotions").isEmpty());
        mvc.perform(get("/api/v1/calendar").param("month", "2026-11").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from diary_emotions where diary_id = ?",
                Integer.class, id)).isZero();
        mvc.perform(get("/api/v1/calendar").param("month", "2026-13").header("Authorization", token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_MONTH"));
    }

    @Test
    void developmentSeederPreservesExistingIdentityAndEditedData() {
        var before = jdbc.queryForMap("select id, name, color_hex from emotion_types where code = 'CALM'");
        try {
            jdbc.update("update emotion_types set name = 'preserved-test-name' where code = 'CALM'");
            seeder.run(null);
            var after = jdbc.queryForMap("select id, name, color_hex from emotion_types where code = 'CALM'");
            assertThat(after.get("id")).isEqualTo(before.get("id"));
            assertThat(after.get("name")).isEqualTo("preserved-test-name");
            assertThat(after.get("color_hex")).isEqualTo(before.get("color_hex"));
            assertThat(jdbc.queryForObject("select count(*) from emotion_types where code = 'CALM'", Integer.class))
                    .isEqualTo(1);
        } finally {
            jdbc.update("update emotion_types set name = ? where code = 'CALM'", before.get("name"));
        }
    }

    @Test
    void calendarUsesMostRecentlyConfirmedDiaryOnSameDate() throws Exception {
        var ids = emotionIds();
        String first = diary();
        String second = diary();
        for (String id : List.of(first, second)) {
            mvc.perform(put("/api/v1/diaries/{id}/emotions", id).header("Authorization", token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(selection(ids.get(id.equals(first) ? "CALM" : "HAPPY"))))
                    .andExpect(status().isOk());
        }
        jdbc.update("update diaries set emotion_confirmed_at = '2026-11-11 10:00:00' where id = ?", first);
        jdbc.update("update diaries set emotion_confirmed_at = '2026-11-11 11:00:00' where id = ?", second);
        mvc.perform(get("/api/v1/calendar").param("month", "2026-11").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].primaryEmotion.code").value("HAPPY"))
                .andExpect(jsonPath("$.days[0].diaryIds.length()").value(2));
    }

    @Test
    void concurrentReplacementsLeaveOnePrimaryEmotion() throws Exception {
        var ids = emotionIds();
        String id = diary();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await();
                UUID emotion = UUID.fromString(ids.get("CALM"));
                return emotionService.replace(userId, UUID.fromString(id),
                        new ReplaceDiaryEmotionsRequest(List.of(emotion), emotion));
            });
            var second = workers.submit(() -> {
                start.await();
                UUID emotion = UUID.fromString(ids.get("SAD"));
                return emotionService.replace(userId, UUID.fromString(id),
                        new ReplaceDiaryEmotionsRequest(List.of(emotion), emotion));
            });
            start.countDown();
            first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            second.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from diary_emotions where diary_id = ?",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from diary_emotions where diary_id = ? and is_primary",
                Integer.class, id)).isEqualTo(1);
    }

    private Map<String, String> emotionIds() throws Exception {
        String response = mvc.perform(get("/api/v1/emotion-types").header("Authorization", token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, String> ids = new HashMap<>();
        mapper.readTree(response).path("items").forEach(n -> ids.put(n.path("code").asString(), n.path("id").asString()));
        return ids;
    }

    private String diary() throws Exception {
        String response = mvc.perform(post("/api/v1/diaries").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"2026-11-11\",\"title\":\"제목\",\"content\":\"기록\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("id").asString();
    }

    private String selection(String... ids) {
        return "{\"emotionIds\":[\"" + String.join("\",\"", ids) + "\"],\"primaryEmotionId\":\"" + ids[0] + "\"}";
    }

    private String token() {
        return "Bearer " + jwt.createAccessToken(userId, "test");
    }
}
