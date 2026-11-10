package com.mowoo.mowoo_backend.login.controller;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import com.mowoo.mowoo_backend.global.response.SuccessResponse;
import com.mowoo.mowoo_backend.login.dto.request.KakaoCodeRequest;
import com.mowoo.mowoo_backend.login.dto.response.AuthResponse;
import com.mowoo.mowoo_backend.login.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Tag(name = "Auth", description = "카카오 로그인과 토큰 관리")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final boolean secureCookie;

    public AuthController(AuthService authService, @Value("${auth.cookie.secure}") boolean secureCookie) {
        this.authService = authService;
        this.secureCookie = secureCookie;
    }

    @SecurityRequirements
    @PostMapping(path = "/kakao", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> kakaoLogin(@Valid @RequestBody KakaoCodeRequest request) {
        AuthService.LoginResult login = authService.handleKakaoCode(request.code());
        Duration maxAge = Duration.between(Instant.now(), login.refreshExpiresAt());
        return ResponseEntity.status(CommonSuccessCode.LOGIN_SUCCESS.getStatus())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(login.refreshToken(), maxAge).toString())
                .body(login.response());
    }

    @SecurityRequirements
    @PostMapping("/reissue")
    public AccessTokenResponse reissue(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        String accessToken = authService.reissueAccessToken(refreshToken);
        return new AccessTokenResponse(CommonSuccessCode.TOKEN_REISSUE_SUCCESS, accessToken);
    }

    @PostMapping("/logout")
    public ResponseEntity<SuccessResponse> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("session_hash"));
        BaseCode code = CommonSuccessCode.LOGOUT_SUCCESS;
        return ResponseEntity.status(code.getStatus())
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .body(SuccessResponse.of(code));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
    }

    public record AccessTokenResponse(boolean isSuccess, String code, String message, String accessToken) {

        public AccessTokenResponse(BaseCode code, String accessToken) {
            this(true, code.name(), code.getMessage(), accessToken);
        }
    }
}
