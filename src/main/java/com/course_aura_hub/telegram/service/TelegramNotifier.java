package com.course_aura_hub.telegram.service;

import com.course_aura_hub.notification.dto.NotificationResponse;
import com.course_aura_hub.notification.service.NotificationsCreated;
import com.course_aura_hub.telegram.client.TelegramClient;
import com.course_aura_hub.telegram.client.TelegramException;
import com.course_aura_hub.telegram.config.TelegramProperties;
import com.course_aura_hub.telegram.model.TelegramLink;
import com.course_aura_hub.telegram.repository.TelegramLinkRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Sends committed notifications to connected chats. Calling Telegram can be slow or fail, so it runs on its
 * own threads and never affects the change that caused the notification. A missed message is safe:
 * the notification is saved and stays in the app's feed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramNotifier {

    private final TelegramLinkRepository linkRepository;
    private final TelegramLinkService linkService;
    private final TelegramClient client;
    private final TelegramMessages messages;
    private final TelegramProperties properties;

    // Deliberately not a bean: an Executor bean would replace Spring Boot's default task executor
    private final ExecutorService executor = new ThreadPoolExecutor(
            2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000));

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationsCreated event) {
        if (!properties.isConfigured()) {
            return;
        }
        try {
            executor.execute(() -> deliver(event.byUser()));
        } catch (RejectedExecutionException ex) {
            log.warn("Telegram delivery queue is full, skipping a send");
        }
    }

    private void deliver(Map<UUID, NotificationResponse> byUser) {
        for (TelegramLink link : linkRepository.findByUserIdInAndEnabledTrue(byUser.keySet())) {
            send(link, byUser.get(link.getUserId()));
        }
    }

    private void send(TelegramLink link, NotificationResponse notification) {
        TelegramMessages.Outgoing message = messages.notification(notification);
        try {
            client.sendMessage(link.getChatId(), message.html(), message.button());
        } catch (TelegramException ex) {
            if (ex.isChatUnavailable()) {
                linkService.disconnect(link.getUserId());
                log.info("Disconnected Telegram chat the bot can no longer reach userId={}", link.getUserId());
            } else {
                log.warn("Telegram delivery failed userId={}: {}", link.getUserId(), ex.getMessage());
            }
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
