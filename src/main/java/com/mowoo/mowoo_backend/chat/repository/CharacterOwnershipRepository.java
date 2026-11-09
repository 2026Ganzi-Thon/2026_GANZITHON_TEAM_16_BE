package com.mowoo.mowoo_backend.chat.repository;

import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import org.springframework.data.repository.Repository;

// 대화에서 캐릭터 보유 여부(CHARACTER_NOT_OWNED)만 확인한다.
// character 패키지에 UserCharacterRepository가 생기면 그것으로 바꾼다.
public interface CharacterOwnershipRepository extends Repository<UserCharacter, UserCharacter.Id> {

    boolean existsById(UserCharacter.Id id);
}
