package com.course_aura_hub.dashboard.service;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.email.EmailDeliveryException;
import com.course_aura_hub.common.email.EmailMessage;
import com.course_aura_hub.common.email.EmailSender;
import com.course_aura_hub.common.scheduling.ClusterJobLock;
import com.course_aura_hub.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Sends the weekly report when it is switched on in Settings. Emails go out after the transaction, so a
 * slow mail server never holds a database connection. Only one instance sends it, under a cluster lease.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportJob {

    private final WeeklyReportService reportService;
    private final SettingsService settingsService;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final ClusterJobLock jobLock;

    @Scheduled(cron = "${app.reports.weekly-cron:0 0 8 * * MON}")
    public void run() {
        if (!settingsService.isWeeklyReportEnabled()) {
            return;
        }
        jobLock.runExclusively("weekly-report", Duration.ofMinutes(30), Duration.ofMinutes(5), this::send);
    }

    private void send() {
        String summary = reportService.publish();
        userRepository.findEnabledEmailsByRole(Role.ROLE_ADMIN).forEach(email -> {
            try {
                emailSender.send(new EmailMessage(email, "Your weekly CourseAuraHub report", summary, null));
            } catch (EmailDeliveryException ex) {
                log.warn("Weekly report email not delivered");
            }
        });
    }
}
