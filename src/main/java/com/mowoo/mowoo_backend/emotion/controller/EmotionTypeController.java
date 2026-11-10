package com.mowoo.mowoo_backend.emotion.controller;

import com.mowoo.mowoo_backend.emotion.dto.EmotionTypeResponse;
import com.mowoo.mowoo_backend.emotion.service.EmotionTypeService;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/emotion-types")
@RequiredArgsConstructor
public class EmotionTypeController {
    private final EmotionTypeService service;

    @GetMapping
    @Operation(summary = "감정 유형 목록")
    public ListResponse<EmotionTypeResponse> list() {
        return service.list();
    }
}
