package com.course_aura_hub.notification.push;

import com.course_aura_hub.notification.dto.NotificationResponse;
import com.course_aura_hub.notification.service.NotificationsCreated;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Pushes committed notifications to subscribed browsers, like the Telegram notifier: on its own threads, after
 * commit, so a slow push service never affects the change. A missed push is safe; the feed has everything.
 */
@Slf4j
@Component
public class WebPushNotifier {

    private static final int MAX_TITLE = 120;
    private static final int MAX_BODY = 400;

    private final PushSubscriptionRepository subscriptionRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final WebPushClient client;
    private final WebPushProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Deliberately not a bean: an Executor bean would replace Spring Boot's default task executor
    private final ExecutorService executor = new ThreadPoolExecutor(
            2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000));

    public WebPushNotifier(PushSubscriptionRepository subscriptionRepository, NamedParameterJdbcTemplate jdbc,
                           WebPushClient client, WebPushProperties properties) {
        this.subscriptionRepository = subscriptionRepository;
        this.jdbc = jdbc;
        this.client = client;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationsCreated event) {
        if (!properties.isConfigured() || event.byUser().isEmpty()) {
            return;
        }
        try {
            executor.execute(() -> deliver(event.byUser()));
        } catch (RejectedExecutionException ex) {
            log.warn("Push delivery queue is full, skipping a send");
        }
    }

    private void deliver(Map<UUID, NotificationResponse> byUser) {
        Set<String> muted = new HashSet<>(jdbc.queryForList(
                "select user_id || ':' || type from push_muted_types where user_id in (:users)",
                new MapSqlParameterSource("users", byUser.keySet()), String.class));
        for (PushSubscription subscription : subscriptionRepository.findByUserIdIn(byUser.keySet())) {
            NotificationResponse notification = byUser.get(subscription.getUserId());
            if (notification != null
                    && !muted.contains(subscription.getUserId() + ":" + notification.type().toUpperCase(Locale.ROOT))) {
                send(subscription, notification);
            }
        }
    }

    private void send(PushSubscription subscription, NotificationResponse notification) {
        try {
            int status = client.send(subscription, payload(notification));
            if (status == 404 || status == 410) {
                subscriptionRepository.deleteById(subscription.getId());
                log.info("Removed an expired push subscription userId={}", subscription.getUserId());
            } else if (status >= 200 && status < 300) {
                subscription.setLastSuccessAt(Instant.now());
                subscriptionRepository.save(subscription);
            } else {
                log.warn("Push service answered {} for userId={}", status, subscription.getUserId());
            }
        } catch (IOException | RuntimeException ex) {
            log.warn("Push delivery failed userId={}: {}", subscription.getUserId(), ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** What the service worker receives. Kept small: push services limit messages to about 4 KB. */
    private byte[] payload(NotificationResponse notification) throws JsonProcessingException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", notification.id());
        body.put("type", notification.type());
        body.put("title", truncate(notification.title(), MAX_TITLE));
        body.put("body", truncate(notification.message(), MAX_BODY));
        body.put("url", notification.link());
        return objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
