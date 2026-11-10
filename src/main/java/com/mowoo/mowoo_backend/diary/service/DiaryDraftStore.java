package com.mowoo.mowoo_backend.diary.service;

import com.mowoo.mowoo_backend.chat.exception.ChatErrorCode;
import com.mowoo.mowoo_backend.chat.repository.ChatSessionRepository;
import com.mowoo.mowoo_backend.diary.ai.DiaryAiResult;
import com.mowoo.mowoo_backend.diary.dto.response.DiaryResponse;
import com.mowoo.mowoo_backend.diary.entity.Diary;
import com.mowoo.mowoo_backend.diary.repository.DiaryRepository;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiaryDraftStore {
    private final DiaryRepository diaries;
    private final ChatSessionRepository sessions;
    private final DiaryResponseMapper mapper;

    @Transactional(readOnly = true)
    public Optional<DiaryResponse> existing(UUID userId, UUID sessionId) {
        return diaries.findBySourceSessionIdAndUserId(sessionId, userId).map(mapper::response);
    }

    // 외부 호출이 끝난 뒤 짧게 원본 세션을 잠그고 중복 여부를 다시 확인한다.
    @Transactional
    public DiaryResponse save(UUID userId, UUID sessionId, LocalDate entryDate, DiaryAiResult result) {
        var session = sessions.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.SESSION_NOT_FOUND));
        var existing = diaries.findBySourceSessionIdAndUserId(sessionId, userId);
        if (existing.isPresent()) return mapper.response(existing.get());
        Diary diary = diaries.saveAndFlush(Diary.draft(session.getUser(), session, entryDate,
                result.title(), result.content()));
        return mapper.response(diary);
    }
}
