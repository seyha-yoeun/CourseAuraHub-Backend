package com.course_aura_hub.reminder.service;

import com.course_aura_hub.reminder.config.ReminderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Running several instances would send reminders from each, as with the weekly report. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderJob {

    private final ReminderService reminderService;
    private final ReminderProperties properties;

    @Scheduled(cron = "${app.reminders.cron:0 0 9 * * *}")
    public void run() {
        if (properties.enabled()) {
            log.info("Sent {} learner reminders", reminderService.sendDue());
        }
    }
}
