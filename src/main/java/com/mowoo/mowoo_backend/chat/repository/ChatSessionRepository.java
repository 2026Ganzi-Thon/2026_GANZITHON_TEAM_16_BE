package com.mowoo.mowoo_backend.chat.repository;

import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    // 다른 사용자의 세션은 없는 세션처럼 다룬다(SESSION_NOT_FOUND).
    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);

    // 메시지 전송·수정 중에는 세션 행을 잠가서 같은 세션의 순번 계산이 겹치지 않게 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChatSession s where s.id = :id and s.user.id = :userId")
    Optional<ChatSession> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    // 보관함 첫 페이지. 최신순이고 시작 시각이 같으면 id 역순이다.
    @Query("""
            select s from ChatSession s
            where s.user.id = :userId
            order by s.startedAt desc, s.id desc
            """)
    List<ChatSession> findLatest(@Param("userId") UUID userId, Limit limit);

    // 보관함 다음 페이지. 커서로 받은 세션보다 뒤에 오는 세션을 같은 순서로 읽는다.
    @Query("""
            select s from ChatSession s
            where s.user.id = :userId
              and (s.startedAt < :startedAt or (s.startedAt = :startedAt and s.id < :id))
            order by s.startedAt desc, s.id desc
            """)
    List<ChatSession> findLatestAfter(
            @Param("userId") UUID userId,
            @Param("startedAt") Instant startedAt,
            @Param("id") UUID id,
            Limit limit
    );
}
