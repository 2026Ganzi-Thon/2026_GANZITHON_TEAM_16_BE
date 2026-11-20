package com.mowoo.mowoo_backend.diary.service;

import com.mowoo.mowoo_backend.diary.dto.response.DiaryItem;
import com.mowoo.mowoo_backend.attachment.service.AttachmentService;
import com.mowoo.mowoo_backend.diary.dto.response.DiaryResponse;
import com.mowoo.mowoo_backend.diary.entity.Diary;
import com.mowoo.mowoo_backend.diary.entity.DiaryEmotion;
import com.mowoo.mowoo_backend.diary.repository.DiaryEmotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DiaryResponseMapper {
    private final DiaryEmotionRepository emotions;
    private final AttachmentService attachments;

    public DiaryResponse response(Diary diary) {
        return DiaryResponse.from(items(List.of(diary)).getFirst());
    }

    public List<DiaryItem> items(List<Diary> diaries) {
        Map<UUID, List<DiaryResponse.Emotion>> grouped = grouped(diaries);
        if (diaries.isEmpty()) return List.of();
        var photos = attachments.diaries(diaries.getFirst().getUser().getId(), diaries.stream().map(Diary::getId).toList());
        return diaries.stream().map(d -> item(d, grouped, photos.getOrDefault(d.getId(), List.of()))).toList();
    }

    private Map<UUID, List<DiaryResponse.Emotion>> grouped(List<Diary> diaries) {
        Map<UUID, List<DiaryResponse.Emotion>> result = new HashMap<>();
        if (diaries.isEmpty()) return result;
        for (DiaryEmotion link : emotions.findForDiaries(diaries.stream().map(Diary::getId).toList())) {
            var type = link.getEmotionType();
            result.computeIfAbsent(link.getDiary().getId(), ignored -> new ArrayList<>())
                    .add(new DiaryResponse.Emotion(type.getId(), type.getCode(), type.getName(),
                            type.getColorHex(), type.getIconUrl(), link.isPrimary()));
        }
        return result;
    }

    private DiaryItem item(Diary diary, Map<UUID, List<DiaryResponse.Emotion>> grouped, List<AttachmentService.View> photos) {
        return new DiaryItem(diary.getId(), diary.getSourceSession() == null ? null : diary.getSourceSession().getId(),
                diary.getEntryDate(), diary.getTitle(), diary.getContent(), diary.getSavedAt(),
                diary.getEmotionConfirmedAt(), List.copyOf(grouped.getOrDefault(diary.getId(), List.of())),
                photos.stream().map(p -> new DiaryResponse.Attachment(p.id(), p.mimeType(), p.url(), p.sortOrder())).toList());
    }
}
