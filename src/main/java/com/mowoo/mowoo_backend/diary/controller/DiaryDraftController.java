package com.mowoo.mowoo_backend.diary.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mowoo.mowoo_backend.diary.dto.response.DiaryResponse;
import com.mowoo.mowoo_backend.diary.service.DiaryDraftService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "diary")
@RestController
@RequestMapping("/api/v1/chat-sessions")
@RequiredArgsConstructor
public class DiaryDraftController {
    private final DiaryDraftService service;

    // 상세 명세의 200을 유지한다. 신규 201/재요청 200 전환은 팀 합의 뒤 적용한다.
    @PutMapping("/{sessionId}/diary-draft")
    @Operation(summary = "AI 일기 초안 생성", description = "사용자 메시지와 AI 응답이 모두 저장된 대화 5왕복 이상에서 초안을 생성합니다. 대화당 하나의 초안을 유지하고 재요청 시 기존 초안을 반환합니다. entryDate는 YYYY-MM-DD이며 생략 시 한국 날짜를 사용합니다. 저장 전 초안은 캘린더·통계에서 제외됩니다.")
    public DiaryResponse generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                  LocalDate entryDate) {
        return service.generate(UUID.fromString(jwt.getSubject()), sessionId, entryDate);
    }
}
