package com.course_aura_hub.file.service;

import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.file.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes uploads that were never finished and files that were replaced. The next run retries anything left over.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupJob {

    private final FileService fileService;
    private final StorageProperties properties;

    @Scheduled(cron = "${app.storage.cleanup-cron:0 37 * * * *}")
    public void cleanUp() {
        if (!properties.enabled()) {
            return;
        }
        try {
            fileService.cleanUp();
        } catch (ServiceUnavailableException ex) {
            log.warn("File cleanup will retry: {}", ex.getMessage());
        }
    }
}
