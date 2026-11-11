package com.mowoo.mowoo_backend.emotion.service;

import com.mowoo.mowoo_backend.emotion.repository.EmotionTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 팀 최종 사전이 아니다. 개발 환경에서 명시적으로 켠 경우에만 누락된 항목을 보완한다.
@Component
@ConditionalOnProperty(name = "mowoo.emotion.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevelopmentEmotionSeeder implements ApplicationRunner {
    private final EmotionTypeRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Figma 94:282, 94:305, 94:312, 94:319의 현재 임시 색상. 아이콘 URL은 미확정이다.
        repository.insertIfAbsent("CALM", "평온한 날", "#3CE892");
        repository.insertIfAbsent("ANGRY", "화났던 날", "#FF8D8E");
        repository.insertIfAbsent("SAD", "우울한 날", "#67B3FF");
        repository.insertIfAbsent("HAPPY", "즐거운 날", "#FFE88A");
    }
}
