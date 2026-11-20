package com.mowoo.mowoo_backend.character.dto.response;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import java.util.UUID;

public record CharacterItem(UUID id, String code, String name, String description, String imageUrl,
                            boolean isPremium, boolean isOwned) {

    public static CharacterItem from(AiCharacter character, boolean owned) {
        return new CharacterItem(character.getId(), character.getCode(), character.getName(),
                character.getDescription(), character.getImageUrl(), character.isPremium(), owned);
    }
}
