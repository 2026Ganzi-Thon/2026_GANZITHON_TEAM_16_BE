package com.mowoo.mowoo_backend.diary.controller;

import com.mowoo.mowoo_backend.diary.dto.request.CreateDiaryRequest;
import com.mowoo.mowoo_backend.diary.dto.request.DiaryListRequest;
import com.mowoo.mowoo_backend.diary.dto.request.UpdateDiaryRequest;
import com.mowoo.mowoo_backend.diary.dto.response.DiaryResponse;
import com.mowoo.mowoo_backend.diary.dto.response.DiaryItem;
import com.mowoo.mowoo_backend.diary.service.DiaryService;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;
import com.mowoo.mowoo_backend.global.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "diary", description = "일기 관련 API")
@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @Operation(summary = "직접 일기 작성", description = "직접 작성한 텍스트 일기를 저장합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiaryResponse createDiary(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody CreateDiaryRequest request) {
        return diaryService.createDiary(userId(jwt), request);
    }

    @Operation(summary = "일기 상세", description = "본인의 일기 또는 초안을 조회합니다.")
    @GetMapping("/{diaryId}")
    public DiaryResponse getDiary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID diaryId) {
        return diaryService.getDiary(userId(jwt), diaryId);
    }

    @Operation(summary = "저장 일기 목록", description = "본인의 저장 일기를 날짜 최신순으로 조회합니다.")
    @GetMapping
    public CursorResponse<DiaryItem> getDiaries(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @ParameterObject DiaryListRequest request) {
        return diaryService.getDiaries(userId(jwt), request);
    }

    @Operation(summary = "일기 수정·초안 저장", description = "일기를 부분 수정하고 save가 true면 초안을 확정합니다.")
    @PatchMapping("/{diaryId}")
    public DiaryResponse updateDiary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID diaryId,
                                     @Valid @RequestBody UpdateDiaryRequest request) {
        return diaryService.updateDiary(userId(jwt), diaryId, request);
    }

    private UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
