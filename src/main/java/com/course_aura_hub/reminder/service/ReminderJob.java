package com.course_aura_hub.reminder.service;

import com.course_aura_hub.common.scheduling.ClusterJobLock;
import com.course_aura_hub.reminder.config.ReminderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Runs on one instance at a time; the lease outlives a quick run so no other instance repeats it. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderJob {

    private final ReminderService reminderService;
    private final ReminderProperties properties;
    private final ClusterJobLock jobLock;

    @Scheduled(cron = "${app.reminders.cron:0 0 9 * * *}")
    public void run() {
        if (properties.enabled()) {
            jobLock.runExclusively("learner-reminders", Duration.ofMinutes(30), Duration.ofMinutes(5),
                    () -> log.info("Sent {} learner reminders", reminderService.sendDue()));
        }
    }
}
