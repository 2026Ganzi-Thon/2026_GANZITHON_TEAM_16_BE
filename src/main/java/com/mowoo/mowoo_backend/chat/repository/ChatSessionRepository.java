package com.mowoo.mowoo_backend.chat.repository;

import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    // 다른 사용자의 세션은 없는 세션처럼 다룬다(SESSION_NOT_FOUND).
    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);
}
