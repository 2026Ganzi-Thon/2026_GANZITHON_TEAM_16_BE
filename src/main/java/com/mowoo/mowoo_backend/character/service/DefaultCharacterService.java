package com.mowoo.mowoo_backend.character.service;

import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import com.mowoo.mowoo_backend.character.repository.AiCharacterRepository;
import com.mowoo.mowoo_backend.character.repository.UserCharacterRepository;
import com.mowoo.mowoo_backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultCharacterService implements ApplicationRunner {

    private static final List<DefaultCharacter> DEFAULTS = List.of(
            new DefaultCharacter("KIND", "다정한 모우", "다정하게 이야기를 들어주는 모우"),
            new DefaultCharacter("COOL", "냉철한 모우", "상황을 차분하게 정리해 주는 모우"),
            new DefaultCharacter("CHEERFUL", "유쾌한 모우", "유쾌하게 기분을 풀어 주는 모우")
    );
    private static final List<String> DEFAULT_CODES = DEFAULTS.stream().map(DefaultCharacter::code).toList();

    private final AiCharacterRepository characterRepository;
    private final UserCharacterRepository ownershipRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (DefaultCharacter character : DEFAULTS) {
            characterRepository.insertDefaultIfAbsent(character.code(), character.name(), character.description());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void grantTo(User user) {
        var ownedIds = ownershipRepository.findCharacterIdsByUserId(user.getId());
        var missing = characterRepository.findByCodeInAndPremiumFalse(DEFAULT_CODES).stream()
                .filter(character -> !ownedIds.contains(character.getId()))
                .map(character -> new UserCharacter(user, character))
                .toList();
        ownershipRepository.saveAll(missing);
    }

    private record DefaultCharacter(String code, String name, String description) {
    }
}
