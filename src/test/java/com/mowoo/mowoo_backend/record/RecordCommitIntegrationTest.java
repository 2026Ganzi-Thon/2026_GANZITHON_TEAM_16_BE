package com.mowoo.mowoo_backend.record;

import com.mowoo.mowoo_backend.character.repository.AiCharacterRepository;
import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.chat.entity.DiaryReference;
import com.mowoo.mowoo_backend.diary.entity.Diary;
import com.mowoo.mowoo_backend.diary.entity.DiaryEmotion;
import com.mowoo.mowoo_backend.emotion.entity.EmotionType;
import com.mowoo.mowoo_backend.login.client.KakaoClient;
import com.mowoo.mowoo_backend.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 테스트 트랜잭션을 사용하지 않아 각 API가 실제로 커밋·롤백된 뒤 별도 연결로 검증한다.
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class RecordCommitIntegrationTest {
    @DynamicPropertySource
    static void requireTestDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("DB_URL");
        if (url == null || !url.startsWith("jdbc:mysql://")
                || !URI.create(url.substring(5)).getPath().matches("/(?:test|test[_-].*|.*[_-]test|.*[_-]test[_-].*)")) {
            throw new IllegalStateException("커밋·삭제 테스트는 전용 MySQL 테스트 DB에서 실행하세요.");
        }
        registry.add("spring.datasource.url", () -> url);
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired AiCharacterRepository characters;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean KakaoClient kakao;
    private Login owner;
    private Login other;
    private Records records;

    @BeforeEach
    void setUpCommittedRecords() throws Exception {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        owner = login();
        other = login();
        records = new TransactionTemplate(transactionManager).execute(status -> {
            User user = em.getReference(User.class, owner.id());
            var character = characters.findByCodeInAndPremiumFalse(List.of("KIND")).getFirst();
            ChatSession session = new ChatSession(user, character);
            ChatSession draftSession = new ChatSession(user, character);
            em.persist(session);
            em.persist(draftSession);
            Diary past = new Diary(user, LocalDate.of(2026, 11, 10), "과거 일기", "private-diary-body", Instant.now());
            Diary saved = Diary.draft(user, session, LocalDate.of(2026, 11, 11), "저장 일기", "저장 본문");
            saved.update(null, null, null, true, Instant.now());
            Diary draft = Diary.draft(user, draftSession, LocalDate.of(2026, 11, 12), "초안", "초안 본문");
            Diary foreign = new Diary(em.getReference(User.class, other.id()), LocalDate.of(2026, 11, 10), "다른 사용자", "본문", Instant.now());
            em.persist(past);
            em.persist(saved);
            em.persist(draft);
            em.persist(foreign);
            em.persist(ChatMessage.ofUser(session, 1, "고민"));
            em.persist(ChatMessage.ofAssistant(session, 2, character, "답변"));
            ChatMessage comparison = ChatMessage.ofAssistant(session, 3, character, "비교 답변");
            em.persist(comparison);
            EmotionType emotion = new EmotionType("commit-" + UUID.randomUUID(), "테스트", "#112233", null);
            em.persist(emotion);
            em.persist(new DiaryEmotion(past, emotion, true));
            em.persist(new DiaryReference(comparison, past, Instant.now()));
            em.flush();
            return new Records(past.getId(), saved.getId(), draft.getId(), session.getId(), comparison.getId(), foreign.getId(), emotion.getId());
        });
    }

    @AfterEach
    void cleanCommittedFixtures() {
        if (owner == null || other == null) return;
        jdbc.update("delete from diaries where user_id in (?, ?)", owner.id().toString(), other.id().toString());
        jdbc.update("delete from chat_sessions where user_id in (?, ?)", owner.id().toString(), other.id().toString());
        jdbc.update("delete from users where id in (?, ?)", owner.id().toString(), other.id().toString());
        if (records != null) jdbc.update("delete from emotion_types where id = ?", records.emotion().toString());
    }

    @Test
    void selectedDeletionCommitsAndAcceptsOneHundredIdsIncludingDuplicates() throws Exception {
        mvc.perform(post("/api/v1/diaries/bulk-delete").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(deletionBody(Collections.nCopies(100, records.past()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("DIARIES_DELETE_SUCCESS"));
        assertThat(countById("diaries", records.past())).isZero();
        assertThat(countById("diaries", records.saved())).isOne();
        assertThat(countById("diaries", records.draft())).isOne();
        assertThat(countById("diaries", records.foreign())).isOne();
        assertThat(countFor("chat_messages", "session_id", records.session())).isEqualTo(3);
        assertThat(countFor("diary_references", "diary_id", records.past())).isZero();
        assertThat(countFor("diary_emotions", "diary_id", records.past())).isZero();
    }

    @Test
    void oversizedDeletionIsRejectedBeforeAnyDataChanges() throws Exception {
        mvc.perform(post("/api/v1/diaries/bulk-delete").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(deletionBody(Collections.nCopies(101, records.past()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertOwnRecordsIntact();
    }

    @Test
    void mixingAnotherUsersDiaryRejectsTheWholeRequest() throws Exception {
        mvc.perform(post("/api/v1/diaries/bulk-delete").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(deletionBody(List.of(records.past(), records.foreign()))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("DIARY_NOT_FOUND"));
        assertOwnRecordsIntact();
        assertThat(countById("diaries", records.foreign())).isOne();
    }

    @Test
    void deletingAllRecordsCommitsButPreservesAccountAndOwnership() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(delete("/api/v1/me/records").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("RECORDS_DELETE_SUCCESS"));
        }
        assertOwnRecordsDeleted();
        assertAccountIntact();
        mvc.perform(post("/api/v1/auth/reissue").cookie(owner.refresh())).andExpect(status().isOk());
    }

    @Test
    void withdrawalCommitsAndRemovesAccountDataAndRefreshSession() throws Exception {
        mvc.perform(delete("/api/v1/auth/withdraw").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("ACCOUNT_DELETE_SUCCESS"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        assertOwnRecordsDeleted();
        assertThat(countById("users", owner.id())).isZero();
        for (String table : List.of("user_auth_identities", "refresh_tokens", "user_characters")) {
            assertThat(countFor(table, "user_id", owner.id())).isZero();
        }
        mvc.perform(post("/api/v1/auth/reissue").cookie(owner.refresh())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, other.bearer())).andExpect(status().isOk());
    }

    @Test
    void blockedWithdrawalRollsBackAllDeletesAndLogsOnlyDatabaseIdentifiers(CapturedOutput output) throws Exception {
        jdbc.execute("""
                create table test_withdrawal_guard (
                    user_id char(36) primary key,
                    constraint fk_test_withdrawal_guard_user foreign key (user_id) references users(id) on delete restrict
                ) engine=InnoDB default charset=utf8mb4
                """);
        try {
            jdbc.update("insert into test_withdrawal_guard(user_id) values (?)", owner.id().toString());
            mvc.perform(delete("/api/v1/auth/withdraw").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ACCOUNT_DELETION_BLOCKED"));
            assertOwnRecordsIntact();
            assertAccountIntact();
            String diagnostics = output.getAll().lines().filter(line -> line.contains("회원 탈퇴 차단:")).reduce("", (a, b) -> a + b);
            assertThat(diagnostics).contains("fk_test_withdrawal_guard_user", "sqlState=23000", "vendorCode=1451")
                    .doesNotContain("private-diary-body", owner.id().toString(), owner.refresh().getValue());
            mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, owner.bearer())).andExpect(status().isOk());
            mvc.perform(post("/api/v1/auth/reissue").cookie(owner.refresh())).andExpect(status().isOk());
        } finally {
            jdbc.execute("drop table test_withdrawal_guard");
        }
    }

    private void assertOwnRecordsIntact() {
        assertThat(countFor("diaries", "user_id", owner.id())).isEqualTo(3);
        assertThat(countFor("chat_sessions", "user_id", owner.id())).isEqualTo(2);
        assertThat(countFor("chat_messages", "session_id", records.session())).isEqualTo(3);
        assertThat(countFor("diary_references", "diary_id", records.past())).isOne();
        assertThat(countFor("diary_emotions", "diary_id", records.past())).isOne();
    }

    private void assertOwnRecordsDeleted() {
        assertThat(countFor("diaries", "user_id", owner.id())).isZero();
        assertThat(countFor("chat_sessions", "user_id", owner.id())).isZero();
        assertThat(countFor("chat_messages", "session_id", records.session())).isZero();
        assertThat(countFor("diary_references", "diary_id", records.past())).isZero();
        assertThat(countFor("diary_emotions", "diary_id", records.past())).isZero();
        assertThat(countById("diaries", records.foreign())).isOne();
        assertThat(countById("users", other.id())).isOne();
    }

    private void assertAccountIntact() {
        assertThat(countById("users", owner.id())).isOne();
        assertThat(countFor("user_characters", "user_id", owner.id())).isEqualTo(3);
        assertThat(countFor("user_auth_identities", "user_id", owner.id())).isOne();
        assertThat(countFor("refresh_tokens", "user_id", owner.id())).isOne();
    }

    private int countById(String table, UUID id) {
        return countFor(table, "id", id);
    }

    private int countFor(String table, String column, UUID id) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Integer.class, id.toString());
    }

    private String deletionBody(List<UUID> ids) {
        return json.writeValueAsString(Map.of("diaryIds", ids));
    }

    private Login login() throws Exception {
        String code = "commit-" + UUID.randomUUID();
        when(kakao.getUser(code)).thenReturn(new KakaoClient.KakaoUser(code, "테스트 사용자", null, null));
        var result = mvc.perform(post("/api/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk()).andReturn();
        var response = json.readTree(result.getResponse().getContentAsByteArray());
        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE).split(";", 2)[0].substring("refresh_token=".length());
        return new Login(UUID.fromString(response.path("id").asString()), "Bearer " + response.path("accessToken").asString(), new Cookie("refresh_token", cookie));
    }

    private record Login(UUID id, String bearer, Cookie refresh) {}
    private record Records(UUID past, UUID saved, UUID draft, UUID session, UUID comparison, UUID foreign, UUID emotion) {}
}
