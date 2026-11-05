package com.course_aura_hub.notification.email;

import com.course_aura_hub.common.scheduling.ClusterJobLock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Sends the daily notification digest from one instance at a time. */
@Component
@RequiredArgsConstructor
public class NotificationDigestJob {

    private final NotificationEmailDelivery delivery;
    private final ClusterJobLock jobLock;

    @Scheduled(cron = "${app.notifications.email.digest-cron:0 0 18 * * *}")
    public void run() {
        jobLock.runExclusively("notification-digest", Duration.ofMinutes(30), Duration.ofMinutes(5), delivery::sendDigests);
    }
}
