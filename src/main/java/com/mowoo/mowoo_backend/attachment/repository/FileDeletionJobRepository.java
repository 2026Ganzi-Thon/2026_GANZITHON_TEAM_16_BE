package com.mowoo.mowoo_backend.attachment.repository;

import com.mowoo.mowoo_backend.attachment.entity.FileDeletionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileDeletionJobRepository extends JpaRepository<FileDeletionJob, UUID> {
    List<FileDeletionJob> findTop50ByNextAttemptAtLessThanEqualOrderByCreatedAtAsc(java.time.Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from FileDeletionJob j where j.id = :id")
    Optional<FileDeletionJob> findForUpdate(UUID id);
}
