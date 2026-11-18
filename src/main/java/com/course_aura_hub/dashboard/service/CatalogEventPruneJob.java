package com.course_aura_hub.dashboard.service;

import com.course_aura_hub.common.scheduling.ClusterJobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Deletes catalog events past the retention period, daily, on one instance at a time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventPruneJob {

    private final CatalogAnalyticsService analyticsService;
    private final ClusterJobLock jobLock;

    @Scheduled(cron = "${app.analytics.prune-cron:0 30 3 * * *}")
    public void run() {
        jobLock.runExclusively("catalog-events-prune", Duration.ofMinutes(30), Duration.ofMinutes(5), () -> {
            int deleted = analyticsService.prune();
            if (deleted > 0) {
                log.info("Pruned {} old catalog events", deleted);
            }
        });
    }
}
