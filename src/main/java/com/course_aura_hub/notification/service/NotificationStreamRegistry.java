package com.course_aura_hub.notification.service;

import com.course_aura_hub.notification.config.NotificationProperties;
import com.course_aura_hub.notification.dto.NotificationResponse;
import com.course_aura_hub.notification.dto.NotificationStreamEvent;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Open streams on this instance. Several instances would each see only their own streams;
 * the rows stay correct either way, and clients catch up from the REST feed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStreamRegistry {

    static final String NOTIFICATION_EVENT = "notification";
    static final String UNREAD_COUNT_EVENT = "unread-count";

    private final NotificationProperties properties;
    private final Map<UUID, Deque<SseEmitter>> streams = new HashMap<>();

    static SseEmitter.SseEventBuilder notificationEvent(NotificationResponse notification, long unreadCount) {
        return SseEmitter.event()
                .id(String.valueOf(notification.id()))
                .name(NOTIFICATION_EVENT)
                .data(new NotificationStreamEvent(notification, unreadCount), MediaType.APPLICATION_JSON);
    }

    public SseEmitter open(UUID userId) {
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onError(ex -> remove(userId, emitter));
        emitter.onTimeout(emitter::complete);

        SseEmitter evicted = null;
        synchronized (streams) {
            Deque<SseEmitter> open = streams.computeIfAbsent(userId, id -> new ArrayDeque<>());
            open.addLast(emitter);
            if (open.size() > properties.maxStreamsPerUser()) {
                evicted = open.pollFirst();
            }
        }
        if (evicted != null) {
            evicted.complete();
        }
        return emitter;
    }

    public boolean isConnected(UUID userId) {
        synchronized (streams) {
            return streams.containsKey(userId);
        }
    }

    public void send(UUID userId, NotificationResponse notification, long unreadCount) {
        streamsOf(userId).forEach(emitter -> send(emitter, notificationEvent(notification, unreadCount)));
    }

    public void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException ex) {
            // The client went away. Its row is still in the feed.
            log.debug("Dropping notification stream: {}", ex.getClass().getSimpleName());
            close(emitter);
        }
    }

    /** Keeps proxies from closing idle streams and clears connections that have died. */
    public void sendHeartbeats() {
        allStreams().forEach(emitter -> send(emitter, SseEmitter.event().comment("heartbeat")));
    }

    @PreDestroy
    public void closeAll() {
        allStreams().forEach(this::close);
    }

    private void close(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ex) {
            log.debug("Notification stream already closed");
        }
    }

    private List<SseEmitter> streamsOf(UUID userId) {
        synchronized (streams) {
            Deque<SseEmitter> open = streams.get(userId);
            return open == null ? List.of() : List.copyOf(open);
        }
    }

    private List<SseEmitter> allStreams() {
        synchronized (streams) {
            return streams.values().stream().flatMap(Deque::stream).toList();
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        synchronized (streams) {
            Deque<SseEmitter> open = streams.get(userId);
            if (open != null) {
                open.remove(emitter);
                if (open.isEmpty()) {
                    streams.remove(userId);
                }
            }
        }
    }
}
