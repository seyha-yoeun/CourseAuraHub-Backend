package com.course_aura_hub.notification.service;

import com.course_aura_hub.common.scheduling.ClusterNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Carries notification pushes between instances over Postgres LISTEN/NOTIFY, so a learner gets a live push
 * whichever instance their stream is open on. Messages hold only notification ids; receivers load the rows.
 *
 * <p>A lost message only costs a live push: the rows are saved and the REST feed and reconnecting streams
 * catch up. The listener holds one pooled connection for as long as the application runs.</p>
 */
@Slf4j
@Component
public class NotificationClusterBridge {

    static final String CHANNEL = "course_aura_notifications";
    private static final int IDS_PER_MESSAGE = 300;
    private static final int POLL_TIMEOUT_MS = 5_000;
    private static final long RETRY_DELAY_MS = 5_000;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final ApplicationEventPublisher events;
    private final ClusterNode node;
    private final boolean enabled;

    private volatile boolean running;
    private Thread listener;

    public NotificationClusterBridge(
            JdbcTemplate jdbcTemplate,
            DataSource dataSource,
            ApplicationEventPublisher events,
            ClusterNode node,
            @Value("${app.notifications.cluster-fanout:true}") boolean enabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.events = events;
        this.node = node;
        this.enabled = enabled;
    }

    /** Tells the other instances about committed notifications. Call outside a transaction. */
    public void publish(List<Long> notificationIds) {
        if (!enabled || notificationIds.isEmpty()) {
            return;
        }
        for (int start = 0; start < notificationIds.size(); start += IDS_PER_MESSAGE) {
            List<Long> chunk = notificationIds.subList(start, Math.min(start + IDS_PER_MESSAGE, notificationIds.size()));
            String payload = node.id() + "|" + chunk.stream().map(String::valueOf).collect(Collectors.joining(","));
            try {
                jdbcTemplate.queryForObject("select pg_notify(?, ?)", Object.class, CHANNEL, payload);
            } catch (DataAccessException ex) {
                log.warn("Could not publish {} notifications to other instances", chunk.size(), ex);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            return;
        }
        running = true;
        listener = Thread.ofPlatform().daemon().name("notification-cluster-listener").start(this::listenLoop);
    }

    @PreDestroy
    void stop() {
        running = false;
        if (listener != null) {
            listener.interrupt();
        }
    }

    private void listenLoop() {
        while (running) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(true);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("LISTEN " + CHANNEL);
                }
                PGConnection pg = connection.unwrap(PGConnection.class);
                log.info("Listening for notifications from other instances");
                while (running) {
                    PGNotification[] received = pg.getNotifications(POLL_TIMEOUT_MS);
                    if (received != null) {
                        Arrays.stream(received).forEach(this::handle);
                    }
                }
            } catch (SQLException | RuntimeException ex) {
                if (!running) {
                    return;
                }
                log.warn("Notification listener lost its connection; retrying in {} ms", RETRY_DELAY_MS, ex);
                sleepBeforeRetry();
            }
        }
    }

    void handle(PGNotification notification) {
        List<Long> ids = parseRemote(notification.getParameter(), node.id());
        if (!ids.isEmpty()) {
            events.publishEvent(new RemoteNotificationsReceived(ids));
        }
    }

    /** Ids from another instance's message; empty for this instance's own messages or a malformed payload. */
    static List<Long> parseRemote(String payload, String ownNodeId) {
        int separator = payload == null ? -1 : payload.indexOf('|');
        if (separator <= 0 || payload.substring(0, separator).equals(ownNodeId)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : payload.substring(separator + 1).split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ex) {
                return List.of();
            }
        }
        return ids;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
