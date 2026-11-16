package com.mowoo.mowoo_backend.emotion.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "emotion")
@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
public class EmotionSuggestionController {
    private final EmotionSuggestionService service;
    @PostMapping("/{diaryId}/emotion-suggestions")
    @Operation(summary = "AI 감정 추천", description = "본인의 일기 내용을 바탕으로 AI 감정 후보를 추천합니다. 추천만으로 일기 감정이 확정되지는 않으며 감정 확정 API를 별도로 호출해야 합니다.")
    public EmotionSuggestionResponse suggest(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID diaryId) {
        return service.suggest(UUID.fromString(jwt.getSubject()), diaryId);
    }
}
