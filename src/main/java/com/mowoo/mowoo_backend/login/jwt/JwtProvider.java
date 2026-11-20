package com.mowoo.mowoo_backend.login.jwt;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class JwtProvider {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshTokenDecoder;
    private final long accessExpMs;
    private final long refreshExpMs;

    public JwtProvider(
            JwtEncoder jwtEncoder,
            @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder,
            @Value("${jwt.access-exp-ms}") long accessExpMs,
            @Value("${jwt.refresh-exp-ms}") long refreshExpMs
    ) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenDecoder = refreshTokenDecoder;
        this.accessExpMs = accessExpMs;
        this.refreshExpMs = refreshExpMs;
    }

    public String createAccessToken(UUID userId, String sessionHash) {
        Instant now = Instant.now();
        return encode(JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusMillis(accessExpMs))
                .claim("type", "ACCESS")
                .claim("session_hash", sessionHash)
                .build()).getTokenValue();
    }

    public Jwt createRefreshToken(UUID userId) {
        Instant now = Instant.now();
        return encode(JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusMillis(refreshExpMs).truncatedTo(ChronoUnit.SECONDS))
                .claim("type", "REFRESH")
                .build());
    }

    public Jwt decodeRefreshToken(String refreshToken) {
        return refreshTokenDecoder.decode(refreshToken);
    }

    private Jwt encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
    }
}
