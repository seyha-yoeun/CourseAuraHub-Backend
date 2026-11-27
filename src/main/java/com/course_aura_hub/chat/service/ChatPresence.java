package com.course_aura_hub.chat.service;

import com.course_aura_hub.chat.config.ChatProperties;
import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.chat.socket.ChatPrincipal;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Who is online in chat, across instances.
 *
 * <p>Each instance counts its own WebSocket sessions per user and keeps one {@code chat_presence} row per user it
 * serves, refreshed every {@code app.chat.presence-heartbeat}. A user is online while any of their rows is connected
 * and fresh (2.5 heartbeats), so the rows of an instance that stopped go stale by themselves. Going offline waits
 * {@code app.chat.presence-grace} first, so the reconnect forced every {@code socket-session-lifetime} doesn't flicker.</p>
 *
 * <p>Status is only for people who share a direct conversation, never across a block, and never for someone who
 * turned "Show when I'm active" off: {@link #forViewer} and {@link ChatDispatcher} apply those rules.</p>
 */
@Slf4j
@Component
public class ChatPresence {

    private final UUID instanceId = UUID.randomUUID();
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final ChatProperties properties;
    private final ScheduledExecutorService graceTimer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("chat-presence-").daemon().factory());

    // This instance's sessions: which user each belongs to, and how many each user has
    private final Map<String, UUID> sessionUsers = new HashMap<>();
    private final Map<UUID, Integer> sessionCounts = new HashMap<>();

    public ChatPresence(NamedParameterJdbcTemplate jdbc, ApplicationEventPublisher events, ChatProperties properties) {
        this.jdbc = jdbc;
        this.events = events;
        this.properties = properties;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        UUID userId = userOf(event.getUser());
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (userId == null || sessionId == null) {
            return;
        }
        boolean first;
        synchronized (this) {
            if (sessionUsers.putIfAbsent(sessionId, userId) != null) {
                return;
            }
            first = sessionCounts.merge(userId, 1, Integer::sum) == 1;
        }
        if (first) {
            markOnline(userId);
        }
    }

    /** May arrive more than once for a session; only the first counts. */
    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        UUID userId;
        boolean last;
        synchronized (this) {
            userId = sessionUsers.remove(event.getSessionId());
            if (userId == null) {
                return;
            }
            last = sessionCounts.computeIfPresent(userId, (ignored, count) -> count > 1 ? count - 1 : null) == null;
        }
        if (last) {
            try {
                graceTimer.schedule(() -> settleOffline(userId), properties.presenceGrace().toMillis(), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ex) {
                // Shutting down: shutdown() marks every row of this instance disconnected
            }
        }
    }

    /** Keeps this instance's rows fresh for everyone connected here. */
    @Scheduled(fixedDelayString = "${app.chat.presence-heartbeat:PT30S}")
    public void heartbeat() {
        List<UUID> connected;
        synchronized (this) {
            connected = List.copyOf(sessionCounts.keySet());
        }
        if (connected.isEmpty()) {
            return;
        }
        try {
            jdbc.batchUpdate(UPSERT_CONNECTED, connected.stream()
                    .map(userId -> (SqlParameterSource) new MapSqlParameterSource("userId", userId).addValue("instance", instanceId))
                    .toArray(SqlParameterSource[]::new));
        } catch (DataAccessException ex) {
            log.warn("Chat presence heartbeat failed", ex);
        }
    }

    /**
     * The status one viewer may see of another person: null when that person hides it or either of them blocked
     * the other.
     */
    public ChatDtos.Presence forViewer(UUID viewerId, UUID userId) {
        if (userId == null || viewerId == null) {
            return null;
        }
        Map<String, Object> row = jdbc.queryForMap("""
                select coalesce((select s.show_presence from chat_settings s where s.user_id = :userId), true)
                         and not exists (select 1 from chat_blocks b
                                         where (b.blocker_id = :viewerId and b.blocked_id = :userId)
                                            or (b.blocker_id = :userId and b.blocked_id = :viewerId)) as shown,
                       coalesce(bool_or(p.connected and p.seen_at > now() - make_interval(secs => :fresh)), false) as online,
                       max(p.seen_at) as last_seen
                from (select 1) one
                left join chat_presence p on p.user_id = :userId
                """, new MapSqlParameterSource("userId", userId).addValue("viewerId", viewerId).addValue("fresh", freshSeconds()));
        return Boolean.TRUE.equals(row.get("shown")) ? presence(row) : null;
    }

    /** The status anyone allowed to see it gets, or null when the person hides it. Blocks are the caller's to check. */
    public ChatDtos.Presence shared(UUID userId) {
        Map<String, Object> row = jdbc.queryForMap("""
                select coalesce((select s.show_presence from chat_settings s where s.user_id = :userId), true) as shown,
                       coalesce(bool_or(p.connected and p.seen_at > now() - make_interval(secs => :fresh)), false) as online,
                       max(p.seen_at) as last_seen
                from (select 1) one
                left join chat_presence p on p.user_id = :userId
                """, new MapSqlParameterSource("userId", userId).addValue("fresh", freshSeconds()));
        return Boolean.TRUE.equals(row.get("shown")) ? presence(row) : null;
    }

    /** Clears out rows left by instances that stopped, keeping each user's newest one for "Active 5m ago". */
    public void prune() {
        int removed = jdbc.update("""
                delete from chat_presence p
                where p.seen_at < now() - interval '1 day'
                  and exists (select 1 from chat_presence q where q.user_id = p.user_id and q.seen_at > p.seen_at)
                """, new MapSqlParameterSource());
        if (removed > 0) {
            log.info("Pruned {} stale chat presence rows", removed);
        }
    }

    @PreDestroy
    void shutdown() {
        graceTimer.shutdownNow();
        try {
            jdbc.update("update chat_presence set connected = false, seen_at = now() where instance_id = :instance and connected",
                    new MapSqlParameterSource("instance", instanceId));
        } catch (DataAccessException ex) {
            log.debug("Could not mark chat presence offline on shutdown", ex);
        }
    }

    // ---- helpers ----

    private static final String UPSERT_CONNECTED = """
            insert into chat_presence (user_id, instance_id, connected, seen_at) values (:userId, :instance, true, now())
            on conflict (user_id, instance_id) do update set connected = true, seen_at = now()
            """;

    private void markOnline(UUID userId) {
        try {
            boolean wasOnline = onlineAnywhere(userId);
            jdbc.update(UPSERT_CONNECTED, new MapSqlParameterSource("userId", userId).addValue("instance", instanceId));
            if (!wasOnline) {
                events.publishEvent(ChatSignal.presence(userId));
            }
        } catch (DataAccessException ex) {
            log.warn("Could not record chat presence for {}", userId, ex);
        }
    }

    private void settleOffline(UUID userId) {
        try {
            synchronized (this) {
                // Back within the grace period: still online
                if (sessionCounts.containsKey(userId)) {
                    return;
                }
                jdbc.update("""
                        update chat_presence set connected = false, seen_at = now()
                        where user_id = :userId and instance_id = :instance
                        """, new MapSqlParameterSource("userId", userId).addValue("instance", instanceId));
            }
            if (!onlineAnywhere(userId)) {
                events.publishEvent(ChatSignal.presence(userId));
            }
        } catch (DataAccessException ex) {
            log.warn("Could not record chat presence for {}", userId, ex);
        }
    }

    private boolean onlineAnywhere(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from chat_presence
                               where user_id = :userId and connected and seen_at > now() - make_interval(secs => :fresh))
                """, new MapSqlParameterSource("userId", userId).addValue("fresh", freshSeconds()), Boolean.class));
    }

    /** A row is fresh for two and a half heartbeats. */
    private double freshSeconds() {
        return properties.presenceHeartbeat().toMillis() * 2.5 / 1000.0;
    }

    private static ChatDtos.Presence presence(Map<String, Object> row) {
        Object lastSeen = row.get("last_seen");
        Instant at = switch (lastSeen) {
            case Timestamp timestamp -> timestamp.toInstant();
            case OffsetDateTime offset -> offset.toInstant();
            case null, default -> null;
        };
        return new ChatDtos.Presence(Boolean.TRUE.equals(row.get("online")), at);
    }

    private static UUID userOf(Principal principal) {
        return principal instanceof ChatPrincipal chat ? chat.userId() : null;
    }
}
