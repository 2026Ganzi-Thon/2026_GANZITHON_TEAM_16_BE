package com.mowoo.mowoo_backend.calendar.service;

import com.mowoo.mowoo_backend.ai.AiUnavailableException;
import com.mowoo.mowoo_backend.calendar.ai.MonthlyRecapAiClient;
import com.mowoo.mowoo_backend.calendar.dto.CalendarRecapResponse;
import com.mowoo.mowoo_backend.calendar.exception.CalendarErrorCode;
import com.mowoo.mowoo_backend.diary.repository.DiaryEmotionRepository;
import com.mowoo.mowoo_backend.diary.repository.DiaryRepository;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarRecapService {
    private final DiaryRepository diaries;
    private final DiaryEmotionRepository emotions;
    private final MonthlyRecapAiClient ai;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CalendarRecapResponse read(UUID userId, String monthValue) {
        var month = CalendarService.parseMonth(monthValue);
        var saved = diaries.findSavedInRange(userId, month.atDay(1), month.atEndOfMonth());
        var counts = emotions.findConfirmedInMonth(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(e -> e.getEmotionType().getId(), Collectors.counting()))
                .entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString()))
                .map(e -> new CalendarRecapResponse.EmotionCount(e.getKey(), e.getValue())).toList();
        if (saved.isEmpty()) return new CalendarRecapResponse(month.toString(), counts, null);
        String summary;
        try { summary = ai.summarize(saved.stream().map(d -> d.getContent()).toList()); }
        catch (AiUnavailableException e) { throw new BusinessException(CalendarErrorCode.AI_UNAVAILABLE); }
        if (summary == null || summary.isBlank()) throw new BusinessException(CalendarErrorCode.AI_UNAVAILABLE);
        return new CalendarRecapResponse(month.toString(), counts, summary);
    }
}
