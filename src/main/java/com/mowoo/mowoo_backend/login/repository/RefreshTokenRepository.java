package com.mowoo.mowoo_backend.login.repository;

import com.mowoo.mowoo_backend.login.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByUserId(UUID userId);

    Optional<RefreshToken> findByUserIdAndTokenHash(UUID userId, String tokenHash);
}
