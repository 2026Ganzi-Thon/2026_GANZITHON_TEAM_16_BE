package com.mowoo.mowoo_backend.login.service;

import com.mowoo.mowoo_backend.character.service.DefaultCharacterService;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.login.client.KakaoClient;
import com.mowoo.mowoo_backend.login.domain.RefreshToken;
import com.mowoo.mowoo_backend.login.domain.UserAuthIdentity;
import com.mowoo.mowoo_backend.login.domain.UserAuthIdentity.Provider;
import com.mowoo.mowoo_backend.login.dto.response.AuthResponse;
import com.mowoo.mowoo_backend.login.exception.AuthErrorCode;
import com.mowoo.mowoo_backend.login.jwt.JwtProvider;
import com.mowoo.mowoo_backend.login.repository.RefreshTokenRepository;
import com.mowoo.mowoo_backend.login.repository.UserAuthIdentityRepository;
import com.mowoo.mowoo_backend.user.entity.User;
import com.mowoo.mowoo_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoClient kakaoClient;
    private final DefaultCharacterService defaultCharacterService;
    private final UserRepository userRepository;
    private final UserAuthIdentityRepository identityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final TransactionTemplate transactions;

    public LoginResult handleKakaoCode(String code) {
        KakaoClient.KakaoUser kakaoUser = kakaoClient.getUser(code);
        return transactions.execute(status -> {
            User user = findOrCreateUser(kakaoUser);
            defaultCharacterService.grantTo(user);
            return issueTokens(user);
        });
    }

    @Transactional(readOnly = true)
    public String reissueAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwtProvider.decodeRefreshToken(refreshToken).getSubject());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String tokenHash = hashToken(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByUserIdAndTokenHash(userId, tokenHash)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        if (stored.getRevokedAt() != null || !stored.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        return jwtProvider.createAccessToken(userId, tokenHash);
    }

    @Transactional
    public void logout(UUID userId, String sessionHash) {
        if (sessionHash == null || userRepository.findByIdForUpdate(userId).isEmpty()) {
            return;
        }

        refreshTokenRepository.findByUserIdAndTokenHash(userId, sessionHash)
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    private User findOrCreateUser(KakaoClient.KakaoUser kakaoUser) {
        byte[] subject = kakaoUser.subject().getBytes(StandardCharsets.UTF_8);
        return identityRepository.findByProviderAndProviderSubject(Provider.KAKAO, subject)
                .map(identity -> {
                    User user = userRepository.findByIdForUpdate(identity.getUser().getId())
                            .orElseThrow(() -> new BusinessException(AuthErrorCode.SOCIAL_IDENTITY_CONFLICT));
                    user.updateProfileImageUrl(kakaoUser.profileImageUrl());
                    return user;
                })
                .orElseGet(() -> registerUser(kakaoUser, subject));
    }

    private User registerUser(KakaoClient.KakaoUser kakaoUser, byte[] subject) {
        String nickname = kakaoUser.nickname().strip();
        if (nickname.isBlank()) {
            nickname = "모우 유저";
        } else if (nickname.codePointCount(0, nickname.length()) > 50) {
            nickname = nickname.substring(0, nickname.offsetByCodePoints(0, 50));
        }

        String email = kakaoUser.email();
        User user = new User(nickname, email != null && email.length() <= 254 ? email : null, null);
        user.updateProfileImageUrl(kakaoUser.profileImageUrl());
        userRepository.save(user);
        try {
            identityRepository.saveAndFlush(new UserAuthIdentity(user, Provider.KAKAO, subject));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(AuthErrorCode.SOCIAL_IDENTITY_CONFLICT);
        }
        return user;
    }

    private LoginResult issueTokens(User user) {
        Jwt refreshToken = jwtProvider.createRefreshToken(user.getId());
        String tokenHash = hashToken(refreshToken.getTokenValue());
        Instant expiresAt = refreshToken.getExpiresAt();

        refreshTokenRepository.findByUserId(user.getId()).ifPresentOrElse(
                stored -> stored.replaceToken(tokenHash, expiresAt),
                () -> refreshTokenRepository.save(new RefreshToken(user, tokenHash, expiresAt))
        );

        String accessToken = jwtProvider.createAccessToken(user.getId(), tokenHash);
        return new LoginResult(new AuthResponse(user.getId(), user.getNickname(), accessToken),
                refreshToken.getTokenValue(), expiresAt);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 초기화할 수 없습니다.", exception);
        }
    }

    public record LoginResult(AuthResponse response, String refreshToken, Instant refreshExpiresAt) {}
}
