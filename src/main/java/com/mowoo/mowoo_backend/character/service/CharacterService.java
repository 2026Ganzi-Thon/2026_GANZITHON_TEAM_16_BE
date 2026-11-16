package com.mowoo.mowoo_backend.character.service;

import com.mowoo.mowoo_backend.character.dto.response.CharacterItem;
import com.mowoo.mowoo_backend.character.dto.response.CharacterResponse;
import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import com.mowoo.mowoo_backend.character.exception.CharacterErrorCode;
import com.mowoo.mowoo_backend.character.repository.AiCharacterRepository;
import com.mowoo.mowoo_backend.character.repository.UserCharacterRepository;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;
import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import com.mowoo.mowoo_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterService {
    private final AiCharacterRepository characters;
    private final UserCharacterRepository ownerships;
    private final UserRepository users;

    public ListResponse<CharacterItem> getCharacters(UUID userId, Boolean owned) {
        requireUser(userId);
        var ownedIds = ownerships.findCharacterIdsByUserId(userId);
        var items = characters.findAll(Sort.by("code")).stream()
                .map(character -> CharacterItem.from(character, ownedIds.contains(character.getId())))
                .filter(character -> owned == null || character.isOwned() == owned)
                .toList();
        return new ListResponse<>(CommonSuccessCode.CHARACTER_LIST_READ_SUCCESS, items);
    }

    public CharacterResponse getCharacter(UUID userId, UUID characterId) {
        requireUser(userId);
        var character = characters.findById(characterId)
                .orElseThrow(() -> new BusinessException(CharacterErrorCode.CHARACTER_NOT_FOUND));
        return CharacterResponse.from(CharacterItem.from(character,
                ownerships.existsById(new UserCharacter.Id(userId, characterId))));
    }

    private void requireUser(UUID userId) {
        if (!users.existsById(userId)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
