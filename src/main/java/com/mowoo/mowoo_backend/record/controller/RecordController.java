package com.mowoo.mowoo_backend.record.controller;

import io.swagger.v3.oas.annotations.Operation;
import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import com.mowoo.mowoo_backend.global.response.SuccessResponse;
import com.mowoo.mowoo_backend.record.dto.request.DeleteDiariesRequest;
import com.mowoo.mowoo_backend.record.service.RecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Records", description = "선택 일기와 전체 기록 삭제")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecordController {
    private final RecordService records;

    @PostMapping("/diaries/bulk-delete")
    @Operation(summary = "선택 일기 삭제", description = "diaryIds에 지정한 본인 일기를 삭제하고 대화는 보존합니다. 중복 ID는 한 번만 처리합니다. 없는 일기 또는 다른 사용자의 일기가 포함되면 404로 전체 요청을 거부합니다.")
    public SuccessResponse deleteDiaries(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody DeleteDiariesRequest request) {
        records.deleteDiaries(UUID.fromString(jwt.getSubject()), request.diaryIds());
        return SuccessResponse.of(CommonSuccessCode.DIARIES_DELETE_SUCCESS);
    }

    @DeleteMapping("/me/records")
    @Operation(summary = "전체 기록 삭제", description = "본인의 저장 일기·초안, 대화와 종속 데이터를 모두 삭제합니다. 계정, 사용자 설정, 캐릭터 보유 정보와 로그인은 유지됩니다. 반복 호출해도 성공합니다.")
    public SuccessResponse deleteRecords(@AuthenticationPrincipal Jwt jwt) {
        records.deleteRecords(UUID.fromString(jwt.getSubject()));
        return SuccessResponse.of(CommonSuccessCode.RECORDS_DELETE_SUCCESS);
    }
}
