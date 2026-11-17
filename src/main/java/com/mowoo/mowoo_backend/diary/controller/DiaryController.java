package com.mowoo.mowoo_backend.diary.controller;

import com.mowoo.mowoo_backend.diary.dto.request.CreateDiaryRequest;
import com.mowoo.mowoo_backend.attachment.web.MultipartPayload;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
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
    private final MultipartPayload payloads;

    @Operation(summary = "직접 일기 작성", description = "entryDate(YYYY-MM-DD), title, content로 본인의 일기를 저장합니다. 텍스트 JSON 요청을 받으며 성공 시 201입니다.")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DiaryResponse createDiary(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody CreateDiaryRequest request) {
        return diaryService.createDiary(userId(jwt), request);
    }

    @Operation(summary = "사진과 함께 일기 작성", description = "payload에는 작성 JSON 문자열, files에는 JPEG·PNG 사진을 반복해서 보냅니다. 기록당 3장·파일당 10MB까지이며 성공 시 201입니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DiaryResponse createDiaryWithFiles(@AuthenticationPrincipal Jwt jwt,
            @RequestPart("payload") String payload,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return diaryService.createDiary(userId(jwt), payloads.read(payload, CreateDiaryRequest.class), files);
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
    @PatchMapping(path = "/{diaryId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DiaryResponse updateDiary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID diaryId,
                                     @Valid @RequestBody UpdateDiaryRequest request) {
        return diaryService.updateDiary(userId(jwt), diaryId, request);
    }

    @Operation(summary = "일기 사진 추가·삭제 및 본문 수정", description = "payload에 수정 JSON과 removeAttachmentIds를 넣고 files에 새 사진을 보냅니다. 삭제 후 남는 사진과 새 사진의 합계는 3장까지입니다. 삭제할 사진도 본인 일기에 속해야 합니다.")
    @PatchMapping(path = "/{diaryId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DiaryResponse updateDiaryWithFiles(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID diaryId,
            @RequestPart("payload") String payload,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return diaryService.updateDiary(userId(jwt), diaryId, payloads.read(payload, UpdateDiaryRequest.class), files);
    }

    private UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
