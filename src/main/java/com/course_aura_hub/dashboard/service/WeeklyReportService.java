package com.course_aura_hub.dashboard.service;

import com.course_aura_hub.enrollment.repository.EnrollmentRepository;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private static final int DAYS = 7;

    private final EnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;

    /** Saves the report as a notification for every admin, which also reaches Telegram, and returns its text. */
    @Transactional
    public String publish() {
        LocalDate since = LocalDate.now().minusDays(DAYS);
        String summary = String.format(
                Locale.ENGLISH,
                "Last %d days: %,d new enrollments and %,.2f in revenue.",
                DAYS,
                enrollmentRepository.countByEnrolledAtGreaterThanEqual(since),
                enrollmentRepository.sumAmountSince(since)
        );
        notificationService.notifyAdmins(NotificationType.WEEKLY_REPORT, "Weekly report", summary, "/analytics");
        return summary;
    }
}
