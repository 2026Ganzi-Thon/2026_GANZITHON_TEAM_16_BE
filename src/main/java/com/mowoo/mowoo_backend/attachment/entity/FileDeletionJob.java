package com.mowoo.mowoo_backend.attachment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

// 계정·일기가 삭제돼도 실제 객체 정리 작업은 남아 재시도할 수 있다.
@Entity
@Table(name = "file_deletion_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileDeletionJob {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    public FileDeletionJob(String objectKey) { this(objectKey, Instant.now()); }
    public FileDeletionJob(String objectKey, Instant nextAttemptAt) {
        this.objectKey=objectKey; this.createdAt=Instant.now(); this.nextAttemptAt=nextAttemptAt;
    }
    public void retryLater() { nextAttemptAt=Instant.now().plusSeconds(300); }
}
