package com.mowoo.mowoo_backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.mowoo.mowoo_backend.user.dto.request.UpdateUserProfileRequest;
import com.mowoo.mowoo_backend.user.dto.response.UserProfileResponse;
import com.mowoo.mowoo_backend.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "User", description = "내 정보와 사용자 설정")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    @Operation(summary = "내 프로필 조회", description = "JWT 사용자 ID로 본인의 닉네임, 이메일, 프로필 이미지, 생년월일, 가입 시각과 과거 일기·저녁 알림 설정을 조회합니다.")
    public UserProfileResponse getUserProfile(@AuthenticationPrincipal Jwt jwt) {
        return userProfileService.getUserProfile(UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping
    @Operation(summary = "내 프로필 수정", description = "전달한 항목만 부분 수정합니다. email과 birthDate는 null로 초기화할 수 있습니다. nickname과 설정 값에 명시적인 null은 허용하지 않습니다. 저녁 알림 설정은 앱의 로컬 예약을 위한 사용자 설정입니다.")
    public UserProfileResponse updateUserProfile(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        return userProfileService.updateUserProfile(UUID.fromString(jwt.getSubject()), request);
    }
}
