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

    @Operation(summary = "직접 일기 작성", description = "entryDate(YYYY-MM-DD), title, content로 본인의 일기를 저장합니다. 텍스트 JSON 요청을 받으며 성공 시 201입니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiaryResponse createDiary(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody CreateDiaryRequest request) {
        return diaryService.createDiary(userId(jwt), request);
    }

    @Operation(summary = "일기 상세", description = "본인의 저장 일기 또는 저장 전 초안을 조회합니다. 다른 사용자 또는 없는 일기는 404입니다.")
    @GetMapping("/{diaryId}")
    public DiaryResponse getDiary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID diaryId) {
        return diaryService.getDiary(userId(jwt), diaryId);
    }

    @Operation(summary = "저장 일기 목록", description = "본인의 저장된 일기를 날짜 최신순으로 조회합니다. from·to는 YYYY-MM-DD, limit은 기본 20·최대 100이며 cursor로 다음 페이지를 요청합니다. 초안은 제외합니다.")
    @GetMapping
    public CursorResponse<DiaryItem> getDiaries(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @ParameterObject DiaryListRequest request) {
        return diaryService.getDiaries(userId(jwt), request);
    }

    @Operation(summary = "일기 수정·초안 저장", description = "전달한 필드만 수정합니다. save가 true면 초안을 저장된 일기로 확정합니다. 저장 후 캘린더·통계에 포함됩니다.")
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
