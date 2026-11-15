package com.mowoo.mowoo_backend.emotion.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mowoo.mowoo_backend.emotion.dto.EmotionTypeResponse;
import com.mowoo.mowoo_backend.emotion.service.EmotionTypeService;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;

@Tag(name = "emotion")
@RestController
@RequestMapping("/api/v1/emotion-types")
@RequiredArgsConstructor
public class EmotionTypeController {
    private final EmotionTypeService service;

    @GetMapping
    @Operation(summary = "감정 유형 목록", description = "일기에 사용할 감정 유형의 ID, 코드, 이름, 색상과 아이콘을 items로 반환합니다. 로그인한 사용자가 조회할 수 있습니다.")
    public ListResponse<EmotionTypeResponse> list() {
        return service.list();
    }
}
