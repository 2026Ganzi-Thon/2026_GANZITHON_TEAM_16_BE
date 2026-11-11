package com.mowoo.mowoo_backend.diary.controller;

import com.mowoo.mowoo_backend.diary.dto.request.ReplaceDiaryEmotionsRequest;
import com.mowoo.mowoo_backend.diary.dto.response.DiaryResponse;
import com.mowoo.mowoo_backend.diary.service.DiaryEmotionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
public class DiaryEmotionController {
    private final DiaryEmotionService service;

    @PutMapping("/{diaryId}/emotions")
    @Operation(summary = "일기 감정 확정·수정")
    public DiaryResponse replace(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID diaryId,
                                 @RequestBody ReplaceDiaryEmotionsRequest request) {
        return service.replace(UUID.fromString(jwt.getSubject()), diaryId, request);
    }
}
