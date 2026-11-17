package com.mowoo.mowoo_backend.attachment.service;

import com.mowoo.mowoo_backend.attachment.entity.FileDeletionJob;
import com.mowoo.mowoo_backend.attachment.repository.FileDeletionJobRepository;
import com.mowoo.mowoo_backend.attachment.repository.AttachmentRepository;
import com.mowoo.mowoo_backend.attachment.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileDeletionWorker {
    private final FileDeletionJobRepository jobs;
    private final ObjectStorage storage;
    private final AttachmentRepository attachments;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(UUID id) {
        jobs.findForUpdate(id).ifPresent(job -> {
            // DB 커밋 직후 프로세스가 종료돼 업로드 정리 예약이 남아도 사용 중인 사진은 보존한다.
            if (attachments.existsByObjectKey(job.getObjectKey())) { jobs.delete(job); return; }
            try {
                storage.delete(job.getObjectKey());
                jobs.delete(job);
            } catch (RuntimeException e) {
                job.retryLater();
                log.warn("사진 삭제를 재시도합니다. jobId={}, type={}", id, e.getClass().getSimpleName());
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID beginUpload(String key) {
        // 업로드 전에 독립적으로 남긴다. 강제 종료 시에도 하루 후 미연결 객체를 회수한다.
        return jobs.saveAndFlush(new FileDeletionJob(key, java.time.Instant.now().plusSeconds(86400))).getId();
    }
}
