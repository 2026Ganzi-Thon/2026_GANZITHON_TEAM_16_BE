package com.mowoo.mowoo_backend.diary.repository;

import com.mowoo.mowoo_backend.diary.entity.DiaryEmotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DiaryEmotionRepository extends JpaRepository<DiaryEmotion, DiaryEmotion.Id> {
    @Query("""
            select e from DiaryEmotion e join fetch e.emotionType
            where e.diary.id in :ids order by e.primary desc, e.emotionType.code asc
            """)
    List<DiaryEmotion> findForDiaries(@Param("ids") Collection<UUID> ids);

    @Modifying
    @Query("delete from DiaryEmotion e where e.diary.id = :id")
    void deleteForDiary(@Param("id") UUID id);

    @Query("""
            select e from DiaryEmotion e join fetch e.diary d join fetch e.emotionType
            where d.user.id = :userId and d.savedAt is not null and d.emotionConfirmedAt is not null
              and d.entryDate between :fromDate and :toDate
            """)
    List<DiaryEmotion> findConfirmedInMonth(@Param("userId") UUID userId,
                                          @Param("fromDate") LocalDate from, @Param("toDate") LocalDate to);
}
