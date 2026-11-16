package com.mowoo.mowoo_backend.login.controller;

import io.swagger.v3.oas.annotations.Operation;
import com.mowoo.mowoo_backend.global.code.BaseCode;
import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import com.mowoo.mowoo_backend.global.response.SuccessResponse;
import com.mowoo.mowoo_backend.login.dto.request.KakaoCodeRequest;
import com.mowoo.mowoo_backend.login.dto.request.SocialCodeRequest;
import com.mowoo.mowoo_backend.login.dto.request.NaverCodeRequest;
import com.mowoo.mowoo_backend.login.dto.response.AuthResponse;
import com.mowoo.mowoo_backend.login.service.AuthService;
import com.mowoo.mowoo_backend.record.service.RecordService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Tag(name = "Auth", description = "소셜 로그인과 토큰 관리")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final RecordService records;
    private final boolean secureCookie;

    public AuthController(AuthService authService, RecordService records, @Value("${auth.cookie.secure}") boolean secureCookie) {
        this.authService = authService;
        this.records = records;
        this.secureCookie = secureCookie;
    }

    @SecurityRequirements
    @PostMapping(path = "/kakao", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "카카오 로그인", description = "카카오 인가 코드를 사용자 정보로 교환한 뒤 가입 또는 로그인합니다. 인가 코드는 한 번만 사용할 수 있습니다. 응답 본문에 액세스 토큰을, HttpOnly 쿠키에 리프레시 토큰을 발급합니다. 로그인 전 호출할 수 있습니다.")
    public ResponseEntity<AuthResponse> kakaoLogin(@Valid @RequestBody KakaoCodeRequest request) {
        return loginResponse(authService.handleKakaoCode(request.code()));
    }

    @SecurityRequirements
    @PostMapping(path = "/google", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "구글 로그인", description = "구글 인가 코드만 전달합니다. 인가 요청의 scope에는 openid, profile, email을 포함하고, redirect_uri는 서버 설정과 동일해야 합니다. 이름·검증된 이메일·프로필 이미지로 가입 또는 로그인하며 액세스 토큰과 리프레시 토큰 쿠키를 발급합니다. 로그인 전 호출할 수 있습니다.")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody SocialCodeRequest request) {
        return loginResponse(authService.handleGoogleCode(request.code()));
    }

    @SecurityRequirements
    @PostMapping(path = "/naver", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "네이버 로그인", description = "네이버 콜백의 code와 state를 전달합니다. 프론트는 로그인 시작 때 생성한 state와 콜백 값을 비교해야 합니다. 회원이름(없으면 별명)·이메일·프로필 이미지로 가입 또는 로그인하며 액세스 토큰과 리프레시 토큰 쿠키를 발급합니다. 로그인 전 호출할 수 있습니다.")
    public ResponseEntity<AuthResponse> naverLogin(@Valid @RequestBody NaverCodeRequest request) {
        return loginResponse(authService.handleNaverCode(request.code(), request.state()));
    }

    private ResponseEntity<AuthResponse> loginResponse(AuthService.LoginResult login) {
        Duration maxAge = Duration.between(Instant.now(), login.refreshExpiresAt());
        return ResponseEntity.status(CommonSuccessCode.LOGIN_SUCCESS.getStatus())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(login.refreshToken(), maxAge).toString())
                .body(login.response());
    }

    @SecurityRequirements
    @PostMapping("/reissue")
    @Operation(summary = "액세스 토큰 재발급", description = "로그인 응답의 refresh_token HttpOnly 쿠키로 새 액세스 토큰을 발급합니다. Authorization 헤더는 필요하지 않습니다. 리프레시 토큰이 누락·만료·폐기되었으면 401을 반환합니다. Swagger에서도 로그인으로 발급받은 쿠키가 필요합니다.")
    public AccessTokenResponse reissue(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        String accessToken = authService.reissueAccessToken(refreshToken);
        return new AccessTokenResponse(CommonSuccessCode.TOKEN_REISSUE_SUCCESS, accessToken);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 액세스 토큰의 세션에 연결된 리프레시 토큰을 폐기하고 쿠키를 만료시킵니다. 이미 발급된 액세스 토큰은 만료 시각까지 유효하며, 로그아웃 후 재발급은 거부됩니다.")
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

    @DeleteMapping("/withdraw")
    @Operation(summary = "회원 탈퇴", description = "본인의 일기·초안과 대화를 먼저 삭제한 뒤 계정, 인증 정보, 리프레시 토큰, 보유 캐릭터를 정리합니다. 리프레시 토큰 쿠키도 만료시킵니다. 성공 시 200과 공통 성공 응답을 반환합니다.")
    public ResponseEntity<SuccessResponse> withdraw(@AuthenticationPrincipal Jwt jwt) {
        records.withdraw(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .body(SuccessResponse.of(CommonSuccessCode.ACCOUNT_DELETE_SUCCESS));
    }

    public record AccessTokenResponse(boolean isSuccess, String code, String message, String accessToken) {

        public AccessTokenResponse(BaseCode code, String accessToken) {
            this(true, code.name(), code.getMessage(), accessToken);
        }
    }
}
