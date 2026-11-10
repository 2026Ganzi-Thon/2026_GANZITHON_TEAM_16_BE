package com.mowoo.mowoo_backend.diary.service;

import com.mowoo.mowoo_backend.diary.dto.request.ReplaceDiaryEmotionsRequest;
import com.mowoo.mowoo_backend.diary.dto.response.DiaryResponse;
import com.mowoo.mowoo_backend.diary.entity.DiaryEmotion;
import com.mowoo.mowoo_backend.diary.exception.DiaryErrorCode;
import com.mowoo.mowoo_backend.diary.repository.DiaryEmotionRepository;
import com.mowoo.mowoo_backend.diary.repository.DiaryRepository;
import com.mowoo.mowoo_backend.emotion.exception.EmotionErrorCode;
import com.mowoo.mowoo_backend.emotion.repository.EmotionTypeRepository;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiaryEmotionService {
    private final DiaryRepository diaries;
    private final DiaryEmotionRepository emotions;
    private final EmotionTypeRepository types;
    private final DiaryResponseMapper mapper;

    @Transactional
    public DiaryResponse replace(UUID userId, UUID diaryId, ReplaceDiaryEmotionsRequest request) {
        var diary = diaries.findOwnedForUpdate(diaryId, userId)
                .orElseThrow(() -> new BusinessException(DiaryErrorCode.DIARY_NOT_FOUND));
        if (diary.getSavedAt() == null) throw new BusinessException(EmotionErrorCode.DIARY_NOT_SAVED);
        if (request == null || request.emotionIds() == null || request.emotionIds().isEmpty()
                || request.emotionIds().stream().anyMatch(java.util.Objects::isNull) || request.primaryEmotionId() == null
                || !request.emotionIds().contains(request.primaryEmotionId())
                || new HashSet<>(request.emotionIds()).size() != request.emotionIds().size()) {
            throw new BusinessException(EmotionErrorCode.INVALID_EMOTIONS);
        }
        var selected = types.findAllById(request.emotionIds());
        if (selected.size() != request.emotionIds().size()) {
            throw new BusinessException(EmotionErrorCode.INVALID_EMOTIONS);
        }
        // 전체를 검증한 뒤 같은 트랜잭션에서 기존 선택을 교체한다.
        emotions.deleteForDiary(diaryId);
        emotions.saveAllAndFlush(selected.stream().map(t -> new DiaryEmotion(diary, t,
                t.getId().equals(request.primaryEmotionId()))).toList());
        diary.confirmEmotions(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        return mapper.response(diary);
    }
}
