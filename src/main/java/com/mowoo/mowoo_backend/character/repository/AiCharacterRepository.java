package com.mowoo.mowoo_backend.character.repository;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AiCharacterRepository extends JpaRepository<AiCharacter, UUID> {

    List<AiCharacter> findByCodeInAndPremiumFalse(Collection<String> codes);

    @Modifying
    @Query(value = """
            INSERT INTO ai_characters (id, code, name, description, is_premium)
            VALUES (UUID(), :code, :name, :description, false)
            ON DUPLICATE KEY UPDATE id = ai_characters.id
            """, nativeQuery = true)
    void insertDefaultIfAbsent(@Param("code") String code, @Param("name") String name,
                               @Param("description") String description);
}
