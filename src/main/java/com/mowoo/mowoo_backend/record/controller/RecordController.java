package com.mowoo.mowoo_backend.record.controller;

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
    public SuccessResponse deleteDiaries(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody DeleteDiariesRequest request) {
        records.deleteDiaries(UUID.fromString(jwt.getSubject()), request.diaryIds());
        return SuccessResponse.of(CommonSuccessCode.DIARIES_DELETE_SUCCESS);
    }

    @DeleteMapping("/me/records")
    public SuccessResponse deleteRecords(@AuthenticationPrincipal Jwt jwt) {
        records.deleteRecords(UUID.fromString(jwt.getSubject()));
        return SuccessResponse.of(CommonSuccessCode.RECORDS_DELETE_SUCCESS);
    }
}
