package com.course_aura_hub.payout.service;

import com.course_aura_hub.common.scheduling.ClusterJobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Makes sales payable once their hold has passed. Runs on one instance at a time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EarningsReleaseJob {

    private final EarningService earningService;
    private final ClusterJobLock jobLock;

    @Scheduled(cron = "${app.payouts.release-cron:0 15 * * * *}")
    public void run() {
        jobLock.runExclusively("instructor-earnings-release", Duration.ofMinutes(10), Duration.ofMinutes(1), () -> {
            int released = earningService.releaseDue();
            if (released > 0) {
                log.info("Released {} instructor earnings for payout", released);
            }
        });
    }
}
