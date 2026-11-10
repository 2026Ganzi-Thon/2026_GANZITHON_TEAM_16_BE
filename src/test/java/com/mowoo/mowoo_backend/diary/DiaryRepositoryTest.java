package com.mowoo.mowoo_backend.diary;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.diary.entity.Diary;
import com.mowoo.mowoo_backend.diary.repository.DiaryRepository;
import com.mowoo.mowoo_backend.diary.service.DiaryService;
import com.mowoo.mowoo_backend.diary.dto.request.CreateDiaryRequest;
import com.mowoo.mowoo_backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DiaryService.class, com.mowoo.mowoo_backend.diary.service.DiaryResponseMapper.class})
class DiaryRepositoryTest extends DiaryDatabaseTest {

    @Autowired private TestEntityManager em;
    @Autowired private DiaryRepository diaries;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DiaryService diaryService;
    @Autowired private PlatformTransactionManager transactionManager;

    private User user;

    @BeforeEach
    void setUp() {
        user = em.persistAndFlush(new User("diary-repository-test", null, null));
    }

    @Test
    void savesUuidAsCharAndReadsAfterClearingPersistenceContext() {
        Diary diary = diaries.saveAndFlush(savedDiary());
        UUID id = diary.getId();
        em.clear();
        Diary reloaded = diaries.findByIdAndUserId(id, user.getId()).orElseThrow();
        assertThat(reloaded.getContent()).isEqualTo("본문");
        assertThat(reloaded.getSavedAt()).isEqualTo(Instant.parse("2026-11-11T00:00:00Z"));
        assertThat(jdbc.queryForObject("select column_type from information_schema.columns "
                + "where table_schema = database() and table_name = 'diaries' and column_name = 'id'",
                String.class)).isEqualToIgnoringCase("char(36)");
        assertThat(diaries.findByIdAndUserId(id, UUID.randomUUID())).isEmpty();
    }

    @Test
    void sourceSessionIsUnique() {
        ChatSession session = session();
        Diary first = diaries.saveAndFlush(savedDiary());
        Diary second = diaries.saveAndFlush(savedDiary());
        linkSession(first, session);
        assertThatThrownBy(() -> linkSession(second, session)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingSourceSessionSetsForeignKeyToNull() {
        ChatSession session = session();
        Diary diary = diaries.saveAndFlush(savedDiary());
        linkSession(diary, session);
        jdbc.update("delete from chat_sessions where id = ?", session.getId().toString());
        em.clear();
        Diary reloaded = diaries.findById(diary.getId()).orElseThrow();
        assertThat(reloaded.getSourceSession()).isNull();
        assertThat(reloaded.getContent()).isEqualTo("본문");
        assertThat(jdbc.queryForObject("select delete_rule from information_schema.referential_constraints "
                + "where constraint_schema = database() and constraint_name = 'fk_diaries_source_session'",
                String.class)).isEqualTo("SET NULL");
    }

    @Test
    void userDeletionIsRestrictedWhileDiaryRemains() {
        diaries.saveAndFlush(savedDiary());
        assertThatThrownBy(() -> jdbc.update("delete from users where id = ?", user.getId().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidUserForeignKeyAndNullContentAreRejectedByDatabase() {
        assertThatThrownBy(() -> jdbc.update("insert into diaries "
                        + "(id, user_id, entry_date, title, content) values (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "2026-11-11", "title", "content"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into diaries "
                        + "(id, user_id, entry_date, title, content) values (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), user.getId().toString(), "2026-11-11", "title", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void serviceWriteParticipatesInTransactionAndRollsBack() {
        // 바깥 테스트 트랜잭션과 별개인 커밋된 사용자로 실제 롤백을 확인한다.
        var tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID committedUser = UUID.randomUUID();
        tx.executeWithoutResult(status -> jdbc.update("insert into users (id, nickname) values (?, ?)",
                committedUser.toString(), "diary-rollback-test"));
        try {
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                diaryService.createDiary(committedUser, new CreateDiaryRequest(
                        LocalDate.of(2026, 11, 11), "title", "content"));
                throw new IllegalStateException("intentional test rollback");
            })).isInstanceOf(IllegalStateException.class);
            Integer count = tx.execute(status -> jdbc.queryForObject(
                    "select count(*) from diaries where user_id = ?", Integer.class, committedUser.toString()));
            assertThat(count).isZero();
        } finally {
            tx.executeWithoutResult(status -> {
                jdbc.update("delete from diaries where user_id = ?", committedUser.toString());
                jdbc.update("delete from users where id = ?", committedUser.toString());
            });
        }
    }

    private Diary savedDiary() {
        return new Diary(user, LocalDate.of(2026, 11, 11), "제목", "본문",
                Instant.parse("2026-11-11T00:00:00Z"));
    }

    private ChatSession session() {
        AiCharacter character = em.persist(new AiCharacter("diary-test-" + UUID.randomUUID(),
                "test character", null, null, false));
        return em.persistAndFlush(new ChatSession(user, character));
    }

    private void linkSession(Diary diary, ChatSession session) {
        jdbc.update("update diaries set source_session_id = ? where id = ?",
                session.getId().toString(), diary.getId().toString());
    }
}
