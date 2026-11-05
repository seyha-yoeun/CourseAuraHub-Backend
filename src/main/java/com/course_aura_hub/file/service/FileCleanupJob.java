package com.course_aura_hub.file.service;

import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.common.scheduling.ClusterJobLock;
import com.course_aura_hub.file.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Removes uploads that were never finished and files that were replaced. The next run retries anything left over.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupJob {

    private final FileService fileService;
    private final StorageProperties properties;
    private final ClusterJobLock jobLock;

    @Scheduled(cron = "${app.storage.cleanup-cron:0 37 * * * *}")
    public void cleanUp() {
        if (!properties.enabled()) {
            return;
        }
        jobLock.runExclusively("file-cleanup", Duration.ofMinutes(30), Duration.ofMinutes(1), () -> {
            try {
                fileService.cleanUp();
            } catch (ServiceUnavailableException ex) {
                log.warn("File cleanup will retry: {}", ex.getMessage());
            }
        });
    }
}
