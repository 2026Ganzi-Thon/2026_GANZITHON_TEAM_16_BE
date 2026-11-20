package com.mowoo.mowoo_backend.character.entity;

import com.mowoo.mowoo_backend.user.entity.User;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_characters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCharacter {

    @EmbeddedId
    private Id id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_user_character_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @MapsId("characterId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_user_character_character"))
    private AiCharacter character;

    @CreationTimestamp
    @Column(name = "acquired_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant acquiredAt;

    public UserCharacter(User user, AiCharacter character) {
        this.id = new Id(user.getId(), character.getId());
        this.user = user;
        this.character = character;
    }

    @Embeddable
    public record Id(
            @Column(name = "user_id", columnDefinition = "CHAR(36)") UUID userId,
            @Column(name = "character_id", columnDefinition = "CHAR(36)") UUID characterId
    ) implements Serializable {
    }
}
