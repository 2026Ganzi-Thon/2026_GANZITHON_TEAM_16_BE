package com.mowoo.mowoo_backend.chat.repository;

import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    // 다음 순번 계산과 최근 메시지 수정 가능 여부 확인에 쓴다.
    Optional<ChatMessage> findTopBySessionIdOrderBySequenceNoDesc(UUID sessionId);

    // ChatTurnPolicy.countCompletedTurns에 넘길 발신자 순서
    @Query("select m.senderRole from ChatMessage m where m.session.id = :sessionId order by m.sequenceNo")
    List<SenderRole> findSenderRolesBySessionId(@Param("sessionId") UUID sessionId);

    // 보관함 목록의 왕복 수를 한 번에 계산한다. 세션별로는 순번 순서가 유지된다.
    @Query("""
            select new com.mowoo.mowoo_backend.chat.repository.SessionSenderRole(m.session.id, m.senderRole)
            from ChatMessage m
            where m.session.id in :sessionIds
            order by m.sequenceNo
            """)
    List<SessionSenderRole> findSenderRolesBySessionIds(@Param("sessionIds") Collection<UUID> sessionIds);

    // 대화 상세 첫 페이지. 최근 메시지부터 읽는다.
    @Query("select m from ChatMessage m where m.session.id = :sessionId order by m.sequenceNo desc")
    List<ChatMessage> findLatest(@Param("sessionId") UUID sessionId, Limit limit);

    // 대화 상세 다음 페이지. 커서 순번보다 앞선 메시지를 최근 것부터 읽는다.
    @Query("""
            select m from ChatMessage m
            where m.session.id = :sessionId and m.sequenceNo < :sequenceNo
            order by m.sequenceNo desc
            """)
    List<ChatMessage> findLatestBefore(
            @Param("sessionId") UUID sessionId,
            @Param("sequenceNo") int sequenceNo,
            Limit limit
    );
}
