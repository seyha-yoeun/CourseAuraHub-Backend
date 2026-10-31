package com.course_aura_hub.notification.service;

import com.course_aura_hub.notification.dto.NotificationPageResponse;
import com.course_aura_hub.notification.dto.NotificationResponse;
import com.course_aura_hub.notification.model.NotificationType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.UUID;

public interface NotificationService {

    /** Saves a notification in the caller's transaction. Students without an account are skipped. */
    void notifyStudent(Integer studentId, NotificationType type, String title, String message, String link);

    /** Like {@link #notifyStudent}, skipping anyone who still has an unread notification of this type and link. */
    void notifyStudentsOnce(Collection<Integer> studentIds, NotificationType type, String title, String message, String link);

    /** Saves a notification for every enabled admin in the caller's transaction. */
    void notifyAdmins(NotificationType type, String title, String message, String link);

    NotificationPageResponse findMine(UUID userId, boolean unreadOnly, int page, int size);

    long countUnread(UUID userId);

    NotificationResponse markRead(UUID userId, Long id);

    int markAllRead(UUID userId);

    void delete(UUID userId, Long id);

    /** @param lastEventId the last row the client saw, if it is reconnecting */
    SseEmitter openStream(UUID userId, String lastEventId);
}
