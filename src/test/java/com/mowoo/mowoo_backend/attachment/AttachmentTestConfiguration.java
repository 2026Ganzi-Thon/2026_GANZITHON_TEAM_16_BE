package com.mowoo.mowoo_backend.attachment;

import com.mowoo.mowoo_backend.attachment.config.DisabledStorageConfiguration;
import com.mowoo.mowoo_backend.attachment.service.AttachmentService;
import com.mowoo.mowoo_backend.attachment.service.FileDeletionService;
import com.mowoo.mowoo_backend.attachment.service.FileDeletionWorker;
import com.mowoo.mowoo_backend.attachment.validation.ImageValidator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import({AttachmentService.class, FileDeletionService.class, FileDeletionWorker.class,
        ImageValidator.class, DisabledStorageConfiguration.class})
public class AttachmentTestConfiguration {}
