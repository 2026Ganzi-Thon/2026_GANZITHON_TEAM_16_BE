package com.mowoo.mowoo_backend.character.dto.response;

import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import java.util.UUID;

public record CharacterResponse(boolean isSuccess, String code, String message, UUID id, String characterCode,
                                String name, String description, String imageUrl, boolean isPremium, boolean isOwned) {

    public static CharacterResponse from(CharacterItem character) {
        var success = CommonSuccessCode.CHARACTER_READ_SUCCESS;
        return new CharacterResponse(true, success.name(), success.getMessage(), character.id(), character.code(),
                character.name(), character.description(), character.imageUrl(), character.isPremium(),
                character.isOwned());
    }
}
