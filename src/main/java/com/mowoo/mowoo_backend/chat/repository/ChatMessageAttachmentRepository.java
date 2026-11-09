package com.mowoo.mowoo_backend.chat.repository;

import com.mowoo.mowoo_backend.chat.entity.ChatMessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatMessageAttachmentRepository extends JpaRepository<ChatMessageAttachment, UUID> {

    // 메시지 목록의 첨부를 한 번에 읽는다.
    List<ChatMessageAttachment> findByMessageIdInOrderBySortOrder(Collection<UUID> messageIds);
}
