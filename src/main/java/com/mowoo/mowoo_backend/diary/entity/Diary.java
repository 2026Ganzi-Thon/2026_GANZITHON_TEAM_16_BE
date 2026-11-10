package com.mowoo.mowoo_backend.diary.entity;

import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "diaries", indexes = {
        @Index(name = "idx_diaries_user_entry_date", columnList = "user_id, entry_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_diaries_source_session", columnNames = "source_session_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_diaries_user"))
    private User user;

    // 대화를 삭제해도 일기는 보존한다. 직접 작성한 일기는 원본 대화가 없다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_session_id", columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_diaries_source_session"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private ChatSession sourceSession;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "saved_at", columnDefinition = "DATETIME")
    private Instant savedAt;

    @Column(name = "emotion_confirmed_at", columnDefinition = "DATETIME")
    private Instant emotionConfirmedAt;

    public Diary(User user, LocalDate entryDate, String title, String content, Instant savedAt) {
        this.user = user;
        this.entryDate = entryDate;
        this.title = title;
        this.content = content;
        this.savedAt = savedAt;
    }

    public static Diary draft(User user, ChatSession source, LocalDate date, String title, String content) {
        Diary diary = new Diary(user, date, title, content, null);
        diary.sourceSession = source;
        return diary;
    }

    public void confirmEmotions(Instant confirmedAt) {
        this.emotionConfirmedAt = confirmedAt;
    }

    public void update(String title, String content, LocalDate entryDate, boolean save, Instant savedAt) {
        if (title != null) {
            this.title = title;
        }
        if (content != null && !content.equals(this.content)) {
            this.content = content;
            this.emotionConfirmedAt = null;
        }
        if (entryDate != null) {
            this.entryDate = entryDate;
        }
        if (save && this.savedAt == null) {
            this.savedAt = savedAt;
        }
    }
}
