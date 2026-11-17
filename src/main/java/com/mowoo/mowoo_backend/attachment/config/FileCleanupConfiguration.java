package com.mowoo.mowoo_backend.attachment.config;

import com.mowoo.mowoo_backend.attachment.repository.FileDeletionJobRepository;
import com.mowoo.mowoo_backend.attachment.service.FileDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "cloud.aws.s3.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FileCleanupConfiguration {
    private final FileDeletionJobRepository jobs;
    private final FileDeletionService deletion;
    @Scheduled(fixedDelayString = "${attachments.cleanup-delay-ms:300000}", initialDelayString = "${attachments.cleanup-delay-ms:300000}")
    public void retry() { jobs.findTop50ByNextAttemptAtLessThanEqualOrderByCreatedAtAsc(java.time.Instant.now())
            .forEach(job -> deletion.attempt(job.getId())); }
}
