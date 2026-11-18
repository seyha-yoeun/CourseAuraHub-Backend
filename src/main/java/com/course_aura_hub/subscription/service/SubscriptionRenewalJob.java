package com.course_aura_hub.subscription.service;

import com.course_aura_hub.common.scheduling.ClusterJobLock;
import com.course_aura_hub.subscription.facade.SubscriptionFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Renews due subscriptions, then ends what is over. One instance at a time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionRenewalJob {

    private final SubscriptionFacade subscriptionFacade;
    private final SubscriptionService subscriptionService;
    private final ClusterJobLock jobLock;

    @Scheduled(cron = "${app.subscriptions.renewal-cron:0 5 * * * *}")
    public void run() {
        jobLock.runExclusively("subscription-renewals", Duration.ofMinutes(30), Duration.ofMinutes(1), () -> {
            int renewed = subscriptionFacade.renewDue();
            int ended = subscriptionService.expireDue();
            if (renewed > 0 || ended > 0) {
                log.info("Subscriptions: {} renewals opened, {} ended", renewed, ended);
            }
        });
    }
}
