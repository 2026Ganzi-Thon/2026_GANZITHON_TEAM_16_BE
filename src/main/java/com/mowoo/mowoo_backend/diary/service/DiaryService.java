package com.mowoo.mowoo_backend.diary.service;

import com.mowoo.mowoo_backend.diary.dto.request.CreateDiaryRequest;
import com.mowoo.mowoo_backend.diary.dto.request.DiaryListRequest;
import com.mowoo.mowoo_backend.diary.dto.request.UpdateDiaryRequest;
import com.mowoo.mowoo_backend.diary.dto.response.DiaryResponse;
import com.mowoo.mowoo_backend.diary.dto.response.DiaryItem;
import com.mowoo.mowoo_backend.diary.entity.Diary;
import com.mowoo.mowoo_backend.diary.exception.DiaryErrorCode;
import com.mowoo.mowoo_backend.diary.repository.DiaryRepository;
import com.mowoo.mowoo_backend.diary.repository.DiaryEmotionRepository;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.response.CursorResponse;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;
import com.mowoo.mowoo_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final DiaryEmotionRepository diaryEmotions;
    private final DiaryResponseMapper mapper;

    @Transactional
    public DiaryResponse createDiary(UUID userId, CreateDiaryRequest request) {
        var user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        // MySQL DATETIME의 초 정밀도와 생성·조회 응답 시각을 일치시킨다.
        Diary diary = diaryRepository.saveAndFlush(new Diary(user, request.entryDate(), request.title(),
                request.content(), Instant.now().truncatedTo(ChronoUnit.SECONDS)));
        return mapper.response(diary);
    }

    public DiaryResponse getDiary(UUID userId, UUID diaryId) {
        Diary diary = diaryRepository.findByIdAndUserId(diaryId, userId)
                .orElseThrow(() -> new BusinessException(DiaryErrorCode.DIARY_NOT_FOUND));
        return mapper.response(diary);
    }

    public CursorResponse<DiaryItem> getDiaries(UUID userId, DiaryListRequest request) {
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw new BusinessException(DiaryErrorCode.INVALID_DATE_RANGE);
        }
        Limit fetchLimit = Limit.of(request.limit() + 1);
        List<Diary> fetched;
        if (request.cursor() == null) {
            fetched = diaryRepository.findSaved(userId, request.from(), request.to(), fetchLimit);
        } else {
            DiaryCursor.Position cursor = DiaryCursor.decode(request.cursor());
            fetched = diaryRepository.findSavedAfter(userId, request.from(), request.to(),
                    cursor.entryDate(), cursor.id(), fetchLimit);
        }
        boolean hasNext = fetched.size() > request.limit();
        List<Diary> page = hasNext ? fetched.subList(0, request.limit()) : fetched;
        String nextCursor = hasNext
                ? DiaryCursor.encode(page.getLast().getEntryDate(), page.getLast().getId()) : null;
        return new CursorResponse<>(mapper.items(page), nextCursor);
    }

    @Transactional
    public DiaryResponse updateDiary(UUID userId, UUID diaryId, UpdateDiaryRequest request) {
        Diary diary = diaryRepository.findOwnedForUpdate(diaryId, userId)
                .orElseThrow(() -> new BusinessException(DiaryErrorCode.DIARY_NOT_FOUND));
        if (request.getContent() != null && !request.getContent().equals(diary.getContent())) {
            diaryEmotions.deleteForDiary(diaryId);
        }
        diary.update(request.getTitle(), request.getContent(), request.getEntryDate(),
                Boolean.TRUE.equals(request.getSave()), Instant.now().truncatedTo(ChronoUnit.SECONDS));
        return mapper.response(diary);
    }
}
