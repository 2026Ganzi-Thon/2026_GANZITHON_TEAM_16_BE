package com.mowoo.mowoo_backend.character.controller;

import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "캐릭터 목록 조회", description = "현재 사용자의 보유 여부(isOwned)를 포함한 캐릭터 목록을 코드순으로 반환합니다. owned 생략은 전체, true는 보유, false는 미보유 캐릭터입니다. 목록은 items에 포함됩니다.")
    public ListResponse<CharacterItem> getCharacters(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Boolean owned) {
        return characters.getCharacters(UUID.fromString(jwt.getSubject()), owned);
    }

    @GetMapping("/{characterId}")
    @Operation(summary = "캐릭터 상세 조회", description = "캐릭터 정보와 현재 사용자의 보유 여부를 반환합니다. 미보유 캐릭터도 조회할 수 있습니다. 최상위 code는 성공 코드이며 characterCode는 캐릭터 식별 코드입니다. 없는 캐릭터는 404입니다.")
    public CharacterResponse getCharacter(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID characterId) {
        return characters.getCharacter(UUID.fromString(jwt.getSubject()), characterId);
    }
}
