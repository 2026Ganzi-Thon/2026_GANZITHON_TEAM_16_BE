package com.mowoo.mowoo_backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 254)
    private String email;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "use_past_diaries", nullable = false)
    @ColumnDefault("false")
    private boolean usePastDiaries;

    @Column(name = "evening_reminder_enabled", nullable = false)
    @ColumnDefault("false")
    private boolean eveningReminderEnabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt;

    public User(String nickname, String email, LocalDate birthDate) {
        this.nickname = nickname;
        this.email = email;
        this.birthDate = birthDate;
    }
}
