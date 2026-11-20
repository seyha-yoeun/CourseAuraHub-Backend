package com.course_aura_hub.live.service;

import com.course_aura_hub.common.scheduling.ClusterJobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Reminds enrolled learners an hour and ten minutes before a live session. One instance at a time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveSessionReminderJob {

    private final LiveSessionService liveSessionService;
    private final ClusterJobLock jobLock;

    @Scheduled(cron = "${app.live.reminder-cron:0 */5 * * * *}")
    public void run() {
        jobLock.runExclusively("live-session-reminders", Duration.ofMinutes(4), Duration.ofSeconds(30), () -> {
            int reminded = liveSessionService.sendReminders();
            if (reminded > 0) {
                log.info("Sent reminders for {} live sessions", reminded);
            }
        });
    }
}
