package com.mowoo.mowoo_backend.calendar.controller;

import com.mowoo.mowoo_backend.calendar.dto.CalendarResponse;
import com.mowoo.mowoo_backend.calendar.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {
    private final CalendarService service;

    @GetMapping
    @Operation(summary = "월간 캘린더")
    public CalendarResponse read(@AuthenticationPrincipal Jwt jwt, @RequestParam String month) {
        return service.read(UUID.fromString(jwt.getSubject()), month);
    }
}
