package com.course_aura_hub.referral.service;

import com.course_aura_hub.common.scheduling.ClusterJobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Credits referrers once the qualifying payment is past the hold. Runs on one instance at a time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferralRewardJob {

    private final ReferralService referralService;
    private final ClusterJobLock jobLock;

    @Scheduled(cron = "${app.referrals.reward-cron:0 40 * * * *}")
    public void run() {
        jobLock.runExclusively("referral-rewards", Duration.ofMinutes(20), Duration.ofMinutes(1), () -> {
            int rewarded = referralService.rewardDue();
            if (rewarded > 0) {
                log.info("Rewarded {} referrals", rewarded);
            }
        });
    }
}
