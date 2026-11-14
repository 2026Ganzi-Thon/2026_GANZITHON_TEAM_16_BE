package com.mowoo.mowoo_backend.calendar.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mowoo.mowoo_backend.calendar.dto.CalendarRecapResponse;
import com.mowoo.mowoo_backend.calendar.service.CalendarRecapService;
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
public class CalendarRecapController {
    private final CalendarRecapService service;
    @GetMapping("/recap")
    @Operation(summary = "월간 마음 회고", description = "month(YYYY-MM)의 본인 저장 일기를 바탕으로 AI 마음 회고와 감정 집계를 반환합니다. 일기가 없으면 회고는 null이며 AI 생성 실패 시 503을 반환합니다.")
    public CalendarRecapResponse read(@AuthenticationPrincipal Jwt jwt,
                                      @Parameter(description = "회고할 한국 기준 연월(YYYY-MM)", example = "2026-11") @RequestParam String month) {
        return service.read(UUID.fromString(jwt.getSubject()), month);
    }
}
