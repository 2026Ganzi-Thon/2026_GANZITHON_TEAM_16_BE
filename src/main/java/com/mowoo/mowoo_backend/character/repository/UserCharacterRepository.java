package com.mowoo.mowoo_backend.character.repository;

import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface UserCharacterRepository extends JpaRepository<UserCharacter, UserCharacter.Id> {

    @Query("select owned.id.characterId from UserCharacter owned where owned.id.userId = :userId")
    Set<UUID> findCharacterIdsByUserId(@Param("userId") UUID userId);
}
