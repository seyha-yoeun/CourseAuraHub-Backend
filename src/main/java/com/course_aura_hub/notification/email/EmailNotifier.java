package com.course_aura_hub.notification.email;

import com.course_aura_hub.notification.service.NotificationsCreated;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Emails instant-mode notifications once their transaction commits, like {@code TelegramNotifier}. Mail can be slow,
 * so it runs on its own threads and never affects the change that caused the notification.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotifier {

    private final NotificationEmailDelivery delivery;

    // Deliberately not a bean: an Executor bean would replace Spring Boot's default task executor
    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000));

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationsCreated event) {
        try {
            executor.execute(() -> {
                try {
                    delivery.sendInstant(event.byUser());
                } catch (RuntimeException ex) {
                    log.warn("Instant notification email failed: {}", ex.getMessage());
                }
            });
        } catch (RejectedExecutionException ex) {
            log.warn("Notification email queue is full, skipping a send");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
