package com.mowoo.mowoo_backend.character.controller;

import com.mowoo.mowoo_backend.character.dto.response.CharacterItem;
import com.mowoo.mowoo_backend.character.dto.response.CharacterResponse;
import com.mowoo.mowoo_backend.character.service.CharacterService;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Character", description = "캐릭터 목록과 상세 조회")
@RestController
@RequestMapping("/api/v1/characters")
@RequiredArgsConstructor
public class CharacterController {
    private final CharacterService characters;

    @GetMapping
    public ListResponse<CharacterItem> getCharacters(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Boolean owned) {
        return characters.getCharacters(UUID.fromString(jwt.getSubject()), owned);
    }

    @GetMapping("/{characterId}")
    public CharacterResponse getCharacter(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID characterId) {
        return characters.getCharacter(UUID.fromString(jwt.getSubject()), characterId);
    }
}
