package com.mowoo.mowoo_backend.diary.entity;

import com.mowoo.mowoo_backend.emotion.entity.EmotionType;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "diary_emotions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryEmotion {
    @EmbeddedId
    private Id id;
    @MapsId("diaryId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diary_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_diary_emotions_diary"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Diary diary;
    @MapsId("emotionTypeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "emotion_type_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_diary_emotions_type"))
    private EmotionType emotionType;
    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    public DiaryEmotion(Diary diary, EmotionType emotionType, boolean primary) {
        this.id = new Id(diary.getId(), emotionType.getId());
        this.diary = diary;
        this.emotionType = emotionType;
        this.primary = primary;
    }

    @Embeddable
    public record Id(@Column(name = "diary_id", columnDefinition = "CHAR(36)") UUID diaryId,
                     @Column(name = "emotion_type_id", columnDefinition = "CHAR(36)") UUID emotionTypeId)
            implements Serializable {
    }
}
