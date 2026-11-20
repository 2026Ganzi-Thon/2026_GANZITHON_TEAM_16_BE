package com.mowoo.mowoo_backend.calendar.service;

import com.mowoo.mowoo_backend.calendar.dto.CalendarResponse;
import com.mowoo.mowoo_backend.calendar.exception.CalendarErrorCode;
import com.mowoo.mowoo_backend.diary.entity.DiaryEmotion;
import com.mowoo.mowoo_backend.diary.repository.DiaryEmotionRepository;
import com.mowoo.mowoo_backend.emotion.dto.EmotionTypeResponse;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {
    private final DiaryEmotionRepository emotions;

    public static YearMonth parseMonth(String value) {
        try {
            if (value == null || !value.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException();
            YearMonth month = YearMonth.parse(value);
            if (month.getYear() < 1000) throw new IllegalArgumentException();
            return month;
        } catch (RuntimeException e) {
            throw new BusinessException(CalendarErrorCode.INVALID_MONTH);
        }
    }

    @Transactional(readOnly = true)
    public CalendarResponse read(UUID userId, String monthValue) {
        YearMonth month = parseMonth(monthValue);
        var byDay = emotions.findConfirmedInMonth(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .filter(DiaryEmotion::isPrimary)
                .collect(Collectors.groupingBy(e -> e.getDiary().getEntryDate(), TreeMap::new, Collectors.toList()));
        List<CalendarResponse.Day> days = byDay.entrySet().stream().map(entry -> {
            var sorted = entry.getValue().stream().sorted(Comparator
                    .comparing((DiaryEmotion e) -> e.getDiary().getEmotionConfirmedAt()).reversed()
                    .thenComparing(e -> e.getDiary().getId().toString(), Comparator.reverseOrder())).toList();
            return new CalendarResponse.Day(entry.getKey(), EmotionTypeResponse.from(sorted.getFirst().getEmotionType()),
                    sorted.stream().map(e -> e.getDiary().getId()).toList());
        }).toList();
        return new CalendarResponse(month.toString(), days);
    }
}
