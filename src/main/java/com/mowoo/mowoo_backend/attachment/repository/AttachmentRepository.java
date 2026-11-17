package com.mowoo.mowoo_backend.attachment.repository;

import com.mowoo.mowoo_backend.attachment.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    boolean existsByObjectKey(String objectKey);
    List<Attachment> findByUserIdAndDiaryIdInOrderBySortOrderAsc(UUID userId, Collection<UUID> ids);
    List<Attachment> findByUserIdAndMessageIdInOrderBySortOrderAsc(UUID userId, Collection<UUID> ids);
    List<Attachment> findByUserIdAndMessageSessionId(UUID userId, UUID sessionId);
    List<Attachment> findByUserId(UUID userId);
    List<Attachment> findByUserIdAndMessageIsNotNull(UUID userId);
}
