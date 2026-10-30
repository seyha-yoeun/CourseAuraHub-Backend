package com.course_aura_hub.notification.service;

import com.course_aura_hub.notification.dto.NotificationResponse;
import com.course_aura_hub.notification.repository.NotificationRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Pushes committed notifications to open streams. Writing to a client can block, so it runs on its own
 * threads and never holds up the request that caused the notification, such as a payment webhook.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationRepository notificationRepository;
    private final NotificationStreamRegistry streamRegistry;

    // Deliberately not a bean: an Executor bean would replace Spring Boot's default task executor
    private final ExecutorService executor = new ThreadPoolExecutor(
            2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000));

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationsCreated event) {
        event.byUser().forEach((userId, notification) -> submit(() -> deliver(userId, notification)));
    }

    @Scheduled(fixedDelayString = "${app.notifications.heartbeat-interval:PT25S}")
    public void heartbeat() {
        submit(streamRegistry::sendHeartbeats);
    }

    private void deliver(UUID userId, NotificationResponse notification) {
        if (streamRegistry.isConnected(userId)) {
            streamRegistry.send(userId, notification, notificationRepository.countByUserIdAndReadAtIsNull(userId));
        }
    }

    private void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ex) {
            // Losing a push is safe: the row is saved and clients catch up from the feed
            log.warn("Notification delivery queue is full, skipping a push");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
