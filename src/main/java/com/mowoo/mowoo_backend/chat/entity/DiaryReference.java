package com.mowoo.mowoo_backend.chat.entity;

import com.mowoo.mowoo_backend.diary.entity.Diary;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

// 과거 일기 비교 답변(AI 메시지)과 사용자가 연결에 동의한 원본 일기.
// 메시지나 일기를 지우면 DB 외래키(ON DELETE CASCADE)로 함께 지워진다.
@Entity
@Table(name = "diary_references")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryReference {

    @EmbeddedId
    private Id id;

    @MapsId("messageId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_diary_references_message"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatMessage message;

    @MapsId("diaryId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diary_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_diary_references_diary"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Diary diary;

    @Column(name = "accepted_at", nullable = false, columnDefinition = "DATETIME")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant acceptedAt;

    public DiaryReference(ChatMessage message, Diary diary, Instant acceptedAt) {
        this.id = new Id(message.getId(), diary.getId());
        this.message = message;
        this.diary = diary;
        this.acceptedAt = acceptedAt;
    }

    @Embeddable
    public record Id(
            @Column(name = "message_id", columnDefinition = "CHAR(36)") UUID messageId,
            @Column(name = "diary_id", columnDefinition = "CHAR(36)") UUID diaryId
    ) implements Serializable {
    }
}
