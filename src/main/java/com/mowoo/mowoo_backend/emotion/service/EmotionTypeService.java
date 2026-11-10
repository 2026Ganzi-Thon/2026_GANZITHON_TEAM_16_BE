package com.mowoo.mowoo_backend.emotion.service;

import com.mowoo.mowoo_backend.emotion.dto.EmotionTypeResponse;
import com.mowoo.mowoo_backend.emotion.repository.EmotionTypeRepository;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmotionTypeService {
    private final EmotionTypeRepository repository;

    @Transactional(readOnly = true)
    public ListResponse<EmotionTypeResponse> list() {
        return new ListResponse<>(repository.findAllByOrderByCodeAsc().stream()
                .map(EmotionTypeResponse::from).toList());
    }
}
