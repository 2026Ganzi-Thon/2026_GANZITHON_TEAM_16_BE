package com.mowoo.mowoo_backend.chat.entity;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_sessions", indexes = {
        @Index(name = "idx_chat_sessions_user_date", columnList = "user_id, started_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_chat_sessions_user"))
    private User user;

    // 대화를 시작한 기본 캐릭터. 메시지마다 다른 캐릭터로 응답할 수 있다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_chat_sessions_character"))
    private AiCharacter character;

    @Column(length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant startedAt;

    @Column(name = "ended_at", columnDefinition = "DATETIME")
    private Instant endedAt;

    public ChatSession(User user, AiCharacter character) {
        this.user = user;
        this.character = character;
    }

    public boolean isEnded() {
        return endedAt != null;
    }

    // 요약 생성에 실패하면 title과 summary는 null로 남긴다.
    public void end(String title, String summary, Instant endedAt) {
        this.title = title;
        this.summary = summary;
        this.endedAt = endedAt;
    }
}
