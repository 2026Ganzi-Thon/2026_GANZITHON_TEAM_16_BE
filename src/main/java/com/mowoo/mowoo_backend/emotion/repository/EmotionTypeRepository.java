package com.mowoo.mowoo_backend.emotion.repository;

import com.mowoo.mowoo_backend.emotion.entity.EmotionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EmotionTypeRepository extends JpaRepository<EmotionType, UUID> {
    List<EmotionType> findAllByOrderByCodeAsc();

    @Modifying
    @Query(value = """
            insert into emotion_types (id, code, name, color_hex, icon_url)
            values (UUID(), :code, :name, :color, null)
            on duplicate key update id = emotion_types.id
            """, nativeQuery = true)
    void insertIfAbsent(@Param("code") String code, @Param("name") String name, @Param("color") String color);
}
