package com.mowoo.mowoo_backend.chat.repository;

import com.mowoo.mowoo_backend.chat.entity.DiaryReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DiaryReferenceRepository extends JpaRepository<DiaryReference, DiaryReference.Id> {

    // 대화 상세 한 페이지의 메시지에 연결된 일기를 한 번에 읽는다.
    @Query("""
            select r from DiaryReference r join fetch r.diary
            where r.message.id in :messageIds
            order by r.acceptedAt, r.diary.id
            """)
    List<DiaryReference> findWithDiaryByMessageIds(@Param("messageIds") Collection<UUID> messageIds);
}
