package com.mowoo.mowoo_backend.user.controller;

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

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public UserProfileResponse getUserProfile(@AuthenticationPrincipal Jwt jwt) {
        return userProfileService.getUserProfile(UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping
    public UserProfileResponse updateUserProfile(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        return userProfileService.updateUserProfile(UUID.fromString(jwt.getSubject()), request);
    }
}
