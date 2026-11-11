package com.mowoo.mowoo_backend.diary.dto.request;

import java.util.List;
import java.util.UUID;

// 도메인 오류 INVALID_EMOTIONS로 응답하기 위해 서비스에서 목록 전체를 검증한다.
public record ReplaceDiaryEmotionsRequest(List<UUID> emotionIds, UUID primaryEmotionId) {
}
