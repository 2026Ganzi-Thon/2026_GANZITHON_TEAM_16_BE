package com.mowoo.mowoo_backend.chat.repository;

import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    // 다음 순번 계산과 최근 메시지 수정 가능 여부 확인에 쓴다.
    Optional<ChatMessage> findTopBySessionIdOrderBySequenceNoDesc(UUID sessionId);

    // ChatTurnPolicy.countCompletedTurns에 넘길 발신자 순서
    @Query("select m.senderRole from ChatMessage m where m.session.id = :sessionId order by m.sequenceNo")
    List<SenderRole> findSenderRolesBySessionId(@Param("sessionId") UUID sessionId);
}
