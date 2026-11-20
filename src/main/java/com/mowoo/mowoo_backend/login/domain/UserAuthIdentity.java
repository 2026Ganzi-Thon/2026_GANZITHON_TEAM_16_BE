package com.mowoo.mowoo_backend.login.domain;

import com.mowoo.mowoo_backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_auth_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uq_auth_provider_subject", columnNames = {"provider", "provider_subject"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @Getter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_auth_identity_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Provider provider;

    @Column(name = "provider_subject", nullable = false, updatable = false, length = 255)
    private byte[] providerSubject;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt;

    public UserAuthIdentity(User user, Provider provider, byte[] providerSubject) {
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject.clone();
    }

    public enum Provider {
        GOOGLE, NAVER, KAKAO
    }
}
