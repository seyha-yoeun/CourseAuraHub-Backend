package com.course_aura_hub.reminder.service;

import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.reminder.config.ReminderProperties;
import com.course_aura_hub.reminder.model.LearnerReminder;
import com.course_aura_hub.reminder.repository.LearnerReminderRepository;
import com.course_aura_hub.student.model.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Goes out through NotificationService, so it reaches the app and any connected Telegram chat. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {

    private final LearnerReminderRepository reminderRepository;
    private final LearnerAccountService learnerAccountService;
    private final NotificationService notificationService;
    private final ReminderProperties properties;

    @Override
    @Transactional
    public int sendDue() {
        Instant now = Instant.now();
        List<LearnerReminderRepository.DueReminder> due = reminderRepository.findDue(
                now.minus(Duration.ofDays(properties.idleDays())),
                LocalDate.now().minusDays(properties.idleDays()),
                now.minus(Duration.ofDays(properties.repeatDays())),
                properties.batchSize()
        );
        due.forEach(reminder -> {
            notificationService.notifyStudent(
                    reminder.getStudentId(),
                    NotificationType.COURSE_REMINDER,
                    "Keep going",
                    "Pick up %s where you left off.".formatted(reminder.getTitle()),
                    "/learn/" + reminder.getSlug()
            );
            reminderRepository.save(LearnerReminder.builder()
                    .studentId(reminder.getStudentId())
                    .courseId(reminder.getCourseId())
                    .sentAt(now)
                    .build());
        });
        return due.size();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEnabled(String email) {
        return learnerAccountService.findCurrentStudent(email).map(Student::isRemindersEnabled).orElse(true);
    }

    @Override
    @Transactional
    public boolean setEnabled(String email, boolean enabled) {
        Student student = learnerAccountService.currentStudent(email);
        student.setRemindersEnabled(enabled);
        return enabled;
    }
}
