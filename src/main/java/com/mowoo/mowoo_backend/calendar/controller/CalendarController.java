package com.mowoo.mowoo_backend.calendar.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mowoo.mowoo_backend.calendar.dto.CalendarResponse;
import com.mowoo.mowoo_backend.calendar.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Calendar")
@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {
    private final CalendarService service;

    @GetMapping
    @Operation(summary = "월간 캘린더", description = "month는 YYYY-MM 형식입니다. Asia/Seoul 기준으로 본인이 저장한 일기와 확정한 감정을 조회하며 저장 전 초안은 제외합니다.")
    public CalendarResponse read(@AuthenticationPrincipal Jwt jwt,
                                 @Parameter(description = "조회할 한국 기준 연월(YYYY-MM)", example = "2026-11") @RequestParam String month) {
        return service.read(UUID.fromString(jwt.getSubject()), month);
    }
}
