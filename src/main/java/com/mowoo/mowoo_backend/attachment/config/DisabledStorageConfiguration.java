package com.mowoo.mowoo_backend.attachment.config;

import com.mowoo.mowoo_backend.attachment.exception.AttachmentErrorCode;
import com.mowoo.mowoo_backend.attachment.storage.ObjectStorage;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cloud.aws.s3.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledStorageConfiguration {
    @Bean
    public ObjectStorage disabledObjectStorage() {
        return new ObjectStorage() {
            private BusinessException unavailable() { return new BusinessException(AttachmentErrorCode.STORAGE_UNAVAILABLE); }
            public void put(String key, byte[] bytes, String mimeType) { throw unavailable(); }
            public String signedUrl(String key, String mimeType) { throw unavailable(); }
            public void delete(String key) { throw unavailable(); }
        };
    }
}
