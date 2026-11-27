package com.course_aura_hub.common.cluster;

import com.course_aura_hub.common.scheduling.ClusterNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Carries small messages between instances over Postgres LISTEN/NOTIFY. One pooled connection listens on the
 * channels of every enabled {@link ClusterChannelHandler} for as long as the application runs.
 *
 * <p>Each message is {@code nodeId|body}; an instance ignores its own messages, so publishers handle their own
 * instance directly. Delivery is best effort: a message sent while a listener is reconnecting is lost, so bodies
 * should only carry ids that receivers reload, and nothing may depend on every message arriving.</p>
 */
@Slf4j
@Component
public class ClusterMessageBus {

    private static final Pattern CHANNEL_NAME = Pattern.compile("[a-z0-9_]{1,63}");
    private static final int POLL_TIMEOUT_MS = 5_000;
    private static final long RETRY_DELAY_MS = 5_000;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final ObjectProvider<ClusterChannelHandler> handlerProvider;
    private final ClusterNode node;

    private volatile Map<String, ClusterChannelHandler> handlers = Map.of();
    private volatile boolean running;
    private Thread listener;

    public ClusterMessageBus(
            JdbcTemplate jdbcTemplate,
            DataSource dataSource,
            ObjectProvider<ClusterChannelHandler> handlerProvider,
            ClusterNode node
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.handlerProvider = handlerProvider;
        this.node = node;
    }

    /** Sends a message to the other instances. Call outside a transaction; failures are logged, not thrown. */
    public void publish(String channel, String body) {
        try {
            jdbcTemplate.queryForObject("select pg_notify(?, ?)", Object.class, channel, node.id() + "|" + body);
        } catch (DataAccessException ex) {
            log.warn("Could not publish a message on {} to other instances", channel, ex);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Map<String, ClusterChannelHandler> enabled = handlerProvider.orderedStream()
                .filter(ClusterChannelHandler::enabled)
                .collect(Collectors.toMap(ClusterChannelHandler::channel, Function.identity()));
        enabled.keySet().forEach(channel -> {
            if (!CHANNEL_NAME.matcher(channel).matches()) {
                throw new IllegalStateException("Invalid cluster channel name: " + channel);
            }
        });
        if (enabled.isEmpty()) {
            return;
        }
        handlers = Map.copyOf(enabled);
        running = true;
        listener = Thread.ofPlatform().daemon().name("cluster-message-listener").start(this::listenLoop);
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
                    for (String channel : handlers.keySet()) {
                        statement.execute("LISTEN " + channel);
                    }
                }
                PGConnection pg = connection.unwrap(PGConnection.class);
                log.info("Listening for messages from other instances on {}", handlers.keySet());
                while (running) {
                    PGNotification[] received = pg.getNotifications(POLL_TIMEOUT_MS);
                    if (received != null) {
                        for (PGNotification notification : received) {
                            dispatch(notification.getName(), notification.getParameter());
                        }
                    }
                }
            } catch (SQLException | RuntimeException ex) {
                if (!running) {
                    return;
                }
                log.warn("Cluster listener lost its connection; retrying in {} ms", RETRY_DELAY_MS, ex);
                sleepBeforeRetry();
            }
        }
    }

    void dispatch(String channel, String payload) {
        ClusterChannelHandler handler = handlers.get(channel);
        String body = bodyFromOtherNode(payload, node.id());
        if (handler == null || body == null) {
            return;
        }
        try {
            handler.onMessage(body);
        } catch (RuntimeException ex) {
            log.warn("Cluster handler for {} failed", channel, ex);
        }
    }

    /** The body of another instance's message; null for this instance's own messages or a malformed payload. */
    public static String bodyFromOtherNode(String payload, String ownNodeId) {
        int separator = payload == null ? -1 : payload.indexOf('|');
        if (separator <= 0 || payload.substring(0, separator).equals(ownNodeId)) {
            return null;
        }
        return payload.substring(separator + 1);
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
