package com.mowoo.mowoo_backend.attachment.service;

import com.mowoo.mowoo_backend.attachment.entity.FileDeletionJob;
import com.mowoo.mowoo_backend.attachment.repository.FileDeletionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileDeletionService {
    private final FileDeletionJobRepository jobs;
    private final FileDeletionWorker worker;

    public void afterCommit(Collection<String> keys) {
        if (keys.isEmpty()) return;
        var ids = keys.stream().map(key -> jobs.save(new FileDeletionJob(key)).getId()).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { ids.forEach(FileDeletionService.this::attempt); }
        });
    }

    // 업로드 요청 후 DB 처리·AI 생성이 실패하면 새 객체만 정리한다. 기존 사진은 지우지 않는다.
    public void trackUpload(String key) {
        UUID id = worker.beginUpload(key);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                // 커밋이면 정리 예약만 제거하고, 롤백이면 객체까지 삭제한다.
                attempt(id);
            }
        });
    }

    public void attempt(UUID id) {
        try { worker.delete(id); }
        catch (RuntimeException e) { log.warn("사진 삭제를 재시도합니다. jobId={}, type={}", id, e.getClass().getSimpleName()); }
    }
}
