package com.mowoo.mowoo_backend.emotion.service;

import com.mowoo.mowoo_backend.ai.AiUnavailableException;
import com.mowoo.mowoo_backend.diary.service.DiaryService;
import com.mowoo.mowoo_backend.emotion.ai.EmotionAiClient;
import com.mowoo.mowoo_backend.emotion.ai.EmotionAiResult;
import com.mowoo.mowoo_backend.emotion.dto.EmotionSuggestionResponse;
import com.mowoo.mowoo_backend.emotion.exception.EmotionErrorCode;
import com.mowoo.mowoo_backend.emotion.repository.EmotionTypeRepository;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmotionSuggestionService {
    private final DiaryService diaries;
    private final EmotionTypeRepository types;
    private final EmotionAiClient ai;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EmotionSuggestionResponse suggest(UUID userId, UUID diaryId) {
        var diary = diaries.getDiary(userId, diaryId);
        if (diary.savedAt() == null) throw new BusinessException(EmotionErrorCode.DIARY_NOT_SAVED);
        var allowed = types.findAllByOrderByCodeAsc();
        if (allowed.isEmpty()) throw unavailable();
        EmotionAiResult result;
        try {
            result = ai.suggest(new EmotionAiClient.DiaryText(diary.title(), diary.content()),
                    allowed.stream().map(t -> new EmotionAiClient.Option(t.getCode(), t.getName())).toList());
        } catch (AiUnavailableException e) { throw unavailable(); }
        var byCode = allowed.stream().collect(Collectors.toMap(t -> t.getCode(), Function.identity()));
        if (result == null || result.emotionCodes() == null || result.emotionCodes().isEmpty()
                || result.emotionCodes().stream().anyMatch(java.util.Objects::isNull) || result.primaryEmotionCode() == null
                || new HashSet<>(result.emotionCodes()).size() != result.emotionCodes().size()
                || !result.emotionCodes().contains(result.primaryEmotionCode())
                || !byCode.keySet().containsAll(result.emotionCodes())) throw unavailable();
        // 추천은 감정 관계나 확정 시각을 변경하지 않는다.
        return new EmotionSuggestionResponse(result.emotionCodes().stream().map(c -> byCode.get(c).getId()).toList(),
                byCode.get(result.primaryEmotionCode()).getId());
    }

    private BusinessException unavailable() { return new BusinessException(EmotionErrorCode.AI_UNAVAILABLE); }
}
