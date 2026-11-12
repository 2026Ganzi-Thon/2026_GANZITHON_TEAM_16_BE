package com.mowoo.mowoo_backend.diary.repository;

import com.mowoo.mowoo_backend.diary.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Limit;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DiaryRepository extends JpaRepository<Diary, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Diary d where d.user.id = :userId and d.id in :ids order by d.id")
    List<Diary> findOwnedForDelete(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Diary d where d.user.id = :userId and d.id in :ids")
    void deleteOwnedByIds(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Diary d where d.user.id = :userId")
    void deleteAllForUser(@Param("userId") UUID userId);

    Optional<Diary> findByIdAndUserId(UUID id, UUID userId);

    Optional<Diary> findBySourceSessionIdAndUserId(UUID sourceSessionId, UUID userId);

    @Query("""
            select d from Diary d where d.user.id = :userId and d.savedAt is not null
              and d.entryDate between :fromDate and :toDate order by d.entryDate asc, d.id asc
            """)
    List<Diary> findSavedInRange(@Param("userId") UUID userId, @Param("fromDate") LocalDate from,
                                @Param("toDate") LocalDate to);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Diary d where d.id = :id and d.user.id = :userId")
    Optional<Diary> findOwnedForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select d from Diary d where d.user.id = :userId and d.savedAt is not null
              and d.entryDate >= coalesce(:fromDate, d.entryDate)
              and d.entryDate <= coalesce(:toDate, d.entryDate)
            order by d.entryDate desc, d.id desc
            """)
    List<Diary> findSaved(@Param("userId") UUID userId, @Param("fromDate") LocalDate from,
                          @Param("toDate") LocalDate to, Limit limit);

    @Query("""
            select d from Diary d where d.user.id = :userId and d.savedAt is not null
              and d.entryDate >= coalesce(:fromDate, d.entryDate)
              and d.entryDate <= coalesce(:toDate, d.entryDate)
              and (d.entryDate < :cursorDate or (d.entryDate = :cursorDate and d.id < :cursorId))
            order by d.entryDate desc, d.id desc
            """)
    List<Diary> findSavedAfter(@Param("userId") UUID userId, @Param("fromDate") LocalDate from,
                               @Param("toDate") LocalDate to, @Param("cursorDate") LocalDate cursorDate,
                               @Param("cursorId") UUID cursorId, Limit limit);
}
