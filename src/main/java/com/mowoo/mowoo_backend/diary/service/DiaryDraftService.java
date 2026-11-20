package com.mowoo.mowoo_backend.diary.service;

import com.mowoo.mowoo_backend.ai.AiUnavailableException;
import com.mowoo.mowoo_backend.chat.service.ChatService;
import com.mowoo.mowoo_backend.diary.ai.DiaryAiClient;
import com.mowoo.mowoo_backend.diary.ai.DiaryAiResult;
import com.mowoo.mowoo_backend.diary.dto.request.CreateDiaryRequest;
import com.mowoo.mowoo_backend.diary.dto.response.DiaryResponse;
import com.mowoo.mowoo_backend.diary.exception.DiaryErrorCode;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiaryDraftService {
    private final ChatService chats;
    private final DiaryDraftStore store;
    private final DiaryAiClient ai;
    private final Validator validator;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DiaryResponse generate(UUID userId, UUID sessionId, LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? LocalDate.now(ZoneId.of("Asia/Seoul")) : requestedDate;
        if (date.getYear() < 1000 || date.getYear() > 9999) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        var transcript = chats.getTranscript(userId, sessionId);
        var existing = store.existing(userId, sessionId);
        if (existing.isPresent()) return existing.get();
        if (!transcript.canGenerateDiary()) throw new BusinessException(DiaryErrorCode.MIN_CHAT_TURNS);
        DiaryAiResult result;
        try { result = ai.generate(transcript); }
        catch (AiUnavailableException e) { throw new BusinessException(DiaryErrorCode.AI_UNAVAILABLE); }
        if (result == null || !validator.validate(new CreateDiaryRequest(date, result.title(), result.content())).isEmpty()) {
            throw new BusinessException(DiaryErrorCode.AI_UNAVAILABLE);
        }
        return store.save(userId, sessionId, date, result);
    }
}
