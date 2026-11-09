package com.mowoo.mowoo_backend.global.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Configuration
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(@Value("${jwt.secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("jwt.secret은 UTF-8 기준 32바이트 이상이어야 합니다.");
        }

        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    @Primary
    @Qualifier("accessTokenDecoder")
    public JwtDecoder accessTokenDecoder(SecretKey secretKey) {
        return createDecoder(secretKey, "ACCESS");
    }

    @Bean
    @Qualifier("refreshTokenDecoder")
    public JwtDecoder refreshTokenDecoder(SecretKey secretKey) {
        return createDecoder(secretKey, "REFRESH");
    }

    private JwtDecoder createDecoder(SecretKey secretKey, String expectedType) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> typeValidator = jwt -> {
            String actualType = jwt.getClaimAsString("type");

            if (expectedType.equals(actualType) && jwt.getExpiresAt() != null && hasUserId(jwt)) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "토큰의 유형, 사용자 ID 또는 만료 시각이 올바르지 않습니다.", null)
            );
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(defaultValidator, typeValidator)
        );

        return decoder;
    }

    private boolean hasUserId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            return false;
        }
        try {
            return UUID.fromString(subject).toString().equals(subject);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
