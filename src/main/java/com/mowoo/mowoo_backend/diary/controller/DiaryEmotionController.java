package com.mowoo.mowoo_backend.diary.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "diary")
@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
public class DiaryEmotionController {
    private final DiaryEmotionService service;

    @PutMapping("/{diaryId}/emotions")
    @Operation(summary = "일기 감정 확정·수정", description = "본인 일기의 감정 목록을 요청 값으로 교체하고 확정 시각을 기록합니다. 대표 감정과 감정 ID는 요청 검증 규칙을 따라야 합니다.")
    public DiaryResponse replace(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID diaryId,
                                 @RequestBody ReplaceDiaryEmotionsRequest request) {
        return service.replace(UUID.fromString(jwt.getSubject()), diaryId, request);
    }
}
