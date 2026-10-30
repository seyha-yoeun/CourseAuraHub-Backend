package com.course_aura_hub.notification.service;

import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.notification.dto.NotificationPageResponse;
import com.course_aura_hub.notification.dto.NotificationResponse;
import com.course_aura_hub.notification.dto.UnreadCountResponse;
import com.course_aura_hub.notification.model.Notification;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.repository.NotificationRepository;
import com.course_aura_hub.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final StudentRepository studentRepository;
    private final NotificationStreamRegistry streamRegistry;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyStudent(Integer studentId, NotificationType type, String title, String message, String link) {
        save(studentRepository.findUserIdsByIdIn(List.of(studentId)), type, title, message, link);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyStudentsOnce(
            Collection<Integer> studentIds,
            NotificationType type,
            String title,
            String message,
            String link
    ) {
        if (studentIds.isEmpty()) {
            return;
        }
        List<UUID> userIds = new ArrayList<>(studentRepository.findUserIdsByIdIn(studentIds));
        if (userIds.isEmpty()) {
            return;
        }
        Set<UUID> alreadyTold = notificationRepository.findUserIdsWithUnread(userIds, type, link);
        userIds.removeAll(alreadyTold);
        save(userIds, type, title, message, link);
    }

    private void save(List<UUID> userIds, NotificationType type, String title, String message, String link) {
        if (userIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<Notification> notifications = userIds.stream()
                .map(userId -> Notification.builder()
                        .userId(userId)
                        .type(type)
                        .title(title)
                        .message(message)
                        .link(link)
                        .createdAt(now)
                        .build())
                .toList();

        Map<UUID, NotificationResponse> byUser = new LinkedHashMap<>();
        notificationRepository.saveAll(notifications)
                .forEach(saved -> byUser.put(saved.getUserId(), NotificationResponse.from(saved)));
        eventPublisher.publishEvent(new NotificationsCreated(byUser));
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageResponse findMine(UUID userId, boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "id")
        );
        Page<Notification> notifications = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNull(userId, pageable)
                : notificationRepository.findByUserId(userId, pageable);

        return new NotificationPageResponse(
                notifications.map(NotificationResponse::from).getContent(),
                notifications.getNumber(),
                notifications.getSize(),
                notifications.getTotalElements(),
                notifications.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID userId, Long id) {
        Notification notification = findOwned(userId, id);
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return NotificationResponse.from(notification);
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, Instant.now());
    }

    @Override
    @Transactional
    public void delete(UUID userId, Long id) {
        notificationRepository.delete(findOwned(userId, id));
    }

    // Registered before reading, so a row saved meanwhile is at worst sent twice, never missed
    @Override
    public SseEmitter openStream(UUID userId, String lastEventId) {
        SseEmitter emitter = streamRegistry.open(userId);
        streamRegistry.send(emitter, SseEmitter.event()
                .name(NotificationStreamRegistry.UNREAD_COUNT_EVENT)
                .data(new UnreadCountResponse(countUnread(userId))));

        Long lastSeen = parseId(lastEventId);
        if (lastSeen != null) {
            notificationRepository.findTop50ByUserIdAndIdGreaterThanOrderByIdAsc(userId, lastSeen)
                    .forEach(missed -> streamRegistry.send(
                            emitter,
                            NotificationStreamRegistry.notificationEvent(NotificationResponse.from(missed), countUnread(userId))
                    ));
        }
        return emitter;
    }

    private Long parseId(String lastEventId) {
        if (!StringUtils.hasText(lastEventId)) {
            return null;
        }
        try {
            return Long.valueOf(lastEventId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // Someone else's notification reads as missing, so ids can't be probed
    private Notification findOwned(UUID userId, Long id) {
        return notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
    }
}
