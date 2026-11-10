package com.mowoo.mowoo_backend.emotion.controller;

import com.mowoo.mowoo_backend.emotion.dto.EmotionSuggestionResponse;
import com.mowoo.mowoo_backend.emotion.service.EmotionSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
public class EmotionSuggestionController {
    private final EmotionSuggestionService service;
    @PostMapping("/{diaryId}/emotion-suggestions")
    @Operation(summary = "AI 감정 추천")
    public EmotionSuggestionResponse suggest(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID diaryId) {
        return service.suggest(UUID.fromString(jwt.getSubject()), diaryId);
    }
}
